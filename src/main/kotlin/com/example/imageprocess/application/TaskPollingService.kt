package com.example.imageprocess.application

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.CircuitBreaker
import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.RateLimiter
import com.example.imageprocess.domain.port.outbound.StatusResult
import com.example.imageprocess.domain.port.outbound.TaskEventPublisher
import com.example.imageprocess.domain.port.outbound.TaskRepository
import com.example.imageprocess.domain.port.outbound.WorkerJobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

@Service
class TaskPollingService(
    private val taskRepository: TaskRepository,
    private val imageProcessor: ImageProcessor,
    private val taskEventPublisher: TaskEventPublisher,
    private val rateLimiter: RateLimiter,
    private val circuitBreaker: CircuitBreaker,
    @Value("\${polling.budget-per-tick}") private val budgetPerTick: Int,
    @Value("\${polling.base-delay-ms}") private val baseDelayMs: Long,
    @Value("\${polling.max-delay-ms}") private val maxDelayMs: Long,
    @Value("\${polling.max-retry-count}") private val maxRetryCount: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${polling.fixed-delay}")
    fun pollTasks() =
        runBlocking {
            if (circuitBreaker.isOpen()) {
                log.debug("Circuit breaker is open, skipping poll")
                return@runBlocking
            }

            val tasks = taskRepository.findPollableTasks(Instant.now(), budgetPerTick)
            if (tasks.isEmpty()) return@runBlocking

            val taskIds = tasks.map { it.id }

            coroutineScope {
                taskIds
                    .map { taskId ->
                        async(Dispatchers.IO) { pollAndUpdateTask(taskId) }
                    }.awaitAll()
            }
        }

    @Transactional
    fun pollAndUpdateTask(taskId: String) {
        val task =
            taskRepository.findById(taskId) ?: run {
                log.warn("Task {} not found during poll", taskId)
                return
            }

        if (!rateLimiter.tryAcquire()) {
            log.debug("Rate limiter rejected poll for task {}", task.id)
            return
        }

        val jobId = task.jobId
        if (jobId == null) {
            log.debug("Task {} has no jobId, skipping poll (Kafka consumer handles submit)", task.id)
            return
        }

        val statusResult = imageProcessor.getJobStatus(jobId)
        applyStatusResult(task, statusResult)
    }

    private fun applyStatusResult(
        task: Task,
        result: StatusResult,
    ) {
        when (result) {
            is StatusResult.Success -> {
                circuitBreaker.recordSuccess()
                when (result.status) {
                    WorkerJobStatus.COMPLETED -> {
                        val updated = task.withResult(result.result ?: "").withNextPoll(Instant.MAX)
                        updated.transitionTo(TaskStatus.COMPLETED)
                        taskRepository.save(updated)
                        log.info("Task {} completed", task.id)
                    }

                    WorkerJobStatus.FAILED -> {
                        val updated = task.withFailReason("Mock Worker returned FAILED").withNextPoll(Instant.MAX)
                        updated.transitionTo(TaskStatus.FAILED)
                        taskRepository.save(updated)
                        log.warn("Task {} failed from Mock Worker", task.id)
                    }

                    WorkerJobStatus.PROCESSING -> {
                        if (task.status != TaskStatus.PROCESSING) {
                            task.transitionTo(TaskStatus.PROCESSING)
                        }
                        val updated = task.withNextPoll(computeNextPollAt(task.pollCount))
                        taskRepository.save(updated)
                    }
                }
            }

            is StatusResult.NonRetryableFailure -> {
                failTask(task, result.reason)
            }

            is StatusResult.RetryableFailure -> {
                circuitBreaker.recordFailure()
                log.warn("Task {} poll failed (retryable): {}", task.id, result.reason)
                handlePollFailure(task)
            }
        }
    }

    private fun failTask(
        task: Task,
        reason: String,
    ) {
        log.warn("Task {} failed (non-retryable): {}", task.id, reason)
        val updated = task.withFailReason(reason).withNextPoll(Instant.MAX)
        updated.transitionTo(TaskStatus.FAILED)
        taskRepository.save(updated)
    }

    private fun handlePollFailure(task: Task) {
        if (task.retryCount >= maxRetryCount) {
            failTask(task, "Max retry count exceeded")
            return
        }

        val nextPollAt = computeNextPollAt(task.retryCount)
        val updated = task.withRetry(nextPollAt)
        updated.transitionTo(TaskStatus.RETRY_WAITING)
        taskRepository.save(updated)
    }

    private fun computeNextPollAt(attempt: Int): Instant {
        val backoff = min(baseDelayMs * 2.0.pow(attempt), maxDelayMs.toDouble())
        val jitter = Random.nextLong(0, backoff.toLong().coerceAtLeast(1))
        return Instant.now().plus(Duration.ofMillis(jitter))
    }

    @Transactional
    fun recoverOnStartup() {
        log.info("Recovering incomplete tasks...")
        val incompleteStatuses =
            listOf(
                TaskStatus.PENDING,
                TaskStatus.SUBMITTED,
                TaskStatus.PROCESSING,
                TaskStatus.RETRY_WAITING,
            )
        val tasks = taskRepository.findByStatusIn(incompleteStatuses)

        tasks.forEach { task ->
            if (task.status == TaskStatus.PENDING && task.jobId == null) {
                taskEventPublisher.publishSubmitTask(task.id, task.imageUrl)
                log.info("Republished submit event for pending task {}", task.id)
            } else {
                val nextPollAt = Instant.now().plus(Duration.ofMillis(Random.nextLong(0, baseDelayMs)))
                val updated = task.withNextPoll(nextPollAt)

                if (task.status == TaskStatus.RETRY_WAITING) {
                    updated.transitionTo(TaskStatus.SUBMITTED)
                }

                taskRepository.save(updated)
            }
        }

        log.info("Recovered {} incomplete tasks", tasks.size)
    }
}
