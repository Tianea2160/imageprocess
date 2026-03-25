package com.example.imageprocess.adapter.inbound.kafka

import com.example.imageprocess.adapter.kafka.KafkaTopics
import com.example.imageprocess.adapter.kafka.TaskSubmitMessage
import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskEvent
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.CircuitBreaker
import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.SubmitResult
import com.example.imageprocess.domain.port.outbound.TaskRepository
import com.example.imageprocess.domain.statemachine.core.StateMachine
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.BackOff
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

@Component
class TaskSubmitConsumer(
    private val taskRepository: TaskRepository,
    private val imageProcessor: ImageProcessor,
    private val circuitBreaker: CircuitBreaker,
    private val sm: StateMachine<TaskStatus, TaskEvent, Task>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "4",
        backOff = BackOff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
    )
    @KafkaListener(topics = [KafkaTopics.TASK_SUBMIT], groupId = "\${spring.kafka.consumer.group-id}")
    @Transactional
    fun onTaskSubmit(record: ConsumerRecord<String, TaskSubmitMessage>) {
        val message = record.value()
        log.info("Consuming submit event for task {}", message.taskId)

        val task = taskRepository.findById(message.taskId)
        if (task == null) {
            log.warn("Task {} not found, skipping", message.taskId)
            return
        }
        if (task.state != TaskStatus.PENDING) {
            log.info("Task {} already in state {}, skipping", task.id, task.state)
            return
        }

        when (val result = imageProcessor.submitImage(message.imageUrl)) {
            is SubmitResult.Success -> {
                val nextPollAt =
                    Instant
                        .now()
                        .plus(Duration.ofMillis(2000 + Random.nextLong(0, 2000)))
                val prepared = task.withNextPoll(nextPollAt)
                val updated = sm.fire(prepared, TaskEvent.Submit(result.jobId)).context
                taskRepository.save(updated)
                circuitBreaker.recordSuccess()
                log.info("Task {} submitted with jobId {}", task.id, result.jobId)
            }

            is SubmitResult.NonRetryableFailure -> {
                failTask(task, result.reason)
                log.warn("Task {} failed (non-retryable): {}", task.id, result.reason)
            }

            is SubmitResult.RetryableFailure -> {
                circuitBreaker.recordFailure()
                log.warn("Task {} submit failed (retryable): {}", task.id, result.reason)
                throw RetryableSubmitException(result.reason)
            }
        }
    }

    @DltHandler
    @Transactional
    fun onDlt(record: ConsumerRecord<String, TaskSubmitMessage>) {
        val message = record.value()
        log.warn("Task {} reached DLT after all retries", message.taskId)

        val task = taskRepository.findById(message.taskId) ?: return
        if (task.state.isTerminal()) return

        failTask(task, "Submit failed after all retries (DLT)")
    }

    private fun failTask(
        task: Task,
        reason: String,
    ) {
        val updated = sm.fire(task, TaskEvent.Fail(reason)).context
        taskRepository.save(updated)
    }
}
