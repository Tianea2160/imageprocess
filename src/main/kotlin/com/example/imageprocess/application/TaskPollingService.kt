package com.example.imageprocess.application

import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.CircuitBreaker
import com.example.imageprocess.domain.port.outbound.TaskEventPublisher
import com.example.imageprocess.domain.port.outbound.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

@Service
class TaskPollingService(
    private val taskRepository: TaskRepository,
    private val taskEventPublisher: TaskEventPublisher,
    private val taskPollExecutor: TaskPollExecutor,
    private val circuitBreaker: CircuitBreaker,
    @Value("\${polling.budget-per-tick}") private val budgetPerTick: Int,
    @Value("\${polling.base-delay-ms}") private val baseDelayMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${polling.fixed-delay}")
    suspend fun pollTasks() {
        if (circuitBreaker.isOpen()) {
            log.debug("Circuit breaker is open, skipping poll")
            return
        }

        val tasks = taskRepository.findPollableTasks(Instant.now(), budgetPerTick)
        if (tasks.isEmpty()) return

        coroutineScope {
            tasks
                .map { task ->
                    async(Dispatchers.IO) { taskPollExecutor.pollAndUpdateTask(task) }
                }.awaitAll()
        }
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
