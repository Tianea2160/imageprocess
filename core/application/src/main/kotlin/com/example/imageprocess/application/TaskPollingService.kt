package com.example.imageprocess.application

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskEvent
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.CircuitBreaker
import com.example.imageprocess.domain.port.outbound.TaskEventPublisher
import com.example.imageprocess.domain.port.outbound.TaskRepository
import com.example.imageprocess.domain.statemachine.core.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.random.Random

@Service
@ConditionalOnProperty("polling.fixed-delay")
class TaskPollingService(
    private val taskRepository: TaskRepository,
    private val taskEventPublisher: TaskEventPublisher,
    private val taskPollExecutor: TaskPollExecutor,
    private val circuitBreaker: CircuitBreaker,
    private val sm: StateMachine<TaskStatus, TaskEvent, Task>,
    @Value("\${polling.budget-per-tick}") private val budgetPerTick: Int,
    @Value("\${polling.base-delay-ms}") private val baseDelayMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var running = false

    fun startPolling() {
        running = true
        log.info("Polling started")
    }

    fun stopPolling() {
        running = false
        log.info("Polling stopped")
    }

    fun isPollingRunning(): Boolean = running

    @Scheduled(fixedDelayString = "\${polling.fixed-delay}")
    fun pollTasks() {
        if (!running) return
        if (circuitBreaker.isOpen()) {
            log.debug("Circuit breaker is open, skipping poll")
            return
        }

        val tasks = taskRepository.findPollableTasks(Instant.now(), budgetPerTick)
        if (tasks.isEmpty()) return

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = tasks.map { task -> executor.submit { taskPollExecutor.pollAndUpdateTask(task) } }
            futures.forEach { it.get() }
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
            if (task.state == TaskStatus.PENDING && task.jobId == null) {
                taskEventPublisher.publishSubmitTask(task.id, task.imageUrl)
                log.info("Republished submit event for pending task {}", task.id)
            } else {
                val nextPollAt = Instant.now().plus(Duration.ofMillis(Random.nextLong(0, baseDelayMs)))
                val updated =
                    task.withNextPoll(nextPollAt).let {
                        if (task.state == TaskStatus.RETRY_WAITING) sm.fire(it, TaskEvent.RecoverToSubmitted).context else it
                    }
                taskRepository.save(updated)
            }
        }

        log.info("Recovered {} incomplete tasks", tasks.size)
    }
}
