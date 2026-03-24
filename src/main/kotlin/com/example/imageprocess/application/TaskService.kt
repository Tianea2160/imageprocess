package com.example.imageprocess.application

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.inbound.TaskUseCase
import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.TaskRepository
import com.example.imageprocess.domain.port.outbound.TsidGenerator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

@Service
@Transactional
class TaskService(
    private val taskRepository: TaskRepository,
    private val imageProcessor: ImageProcessor,
    private val tsidGenerator: TsidGenerator,
    @Value("\${polling.initial-delay-ms}") private val initialDelayMs: Long,
    @Value("\${polling.spread-window-ms}") private val spreadWindowMs: Long,
) : TaskUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createTask(imageUrl: String): Task {
        require(imageUrl.isNotBlank()) { "imageUrl must not be blank" }
        require(imageUrl.length <= 2048) { "imageUrl must not exceed 2048 characters" }
        require(imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            "imageUrl must be an HTTP or HTTPS URL"
        }

        val fingerprint = Task.computeFingerprint(imageUrl)

        val existing = taskRepository.findByFingerprint(fingerprint)
        if (existing != null) {
            return existing
        }

        val nextPollAt =
            Instant
                .now()
                .plus(Duration.ofMillis(initialDelayMs))
                .plus(Duration.ofMillis(Random.nextLong(0, spreadWindowMs)))

        val task = Task(id = tsidGenerator.generate(), imageUrl = imageUrl, nextPollAt = nextPollAt)
        val saved = taskRepository.save(task)

        submitToWorkerAsync(saved)

        return saved
    }

    @Transactional(readOnly = true)
    override fun getTask(taskId: String): Task =
        taskRepository.findById(taskId)
            ?: throw NoSuchElementException("Task not found: $taskId")

    @Transactional(readOnly = true)
    override fun listTasks(
        pageable: Pageable,
        status: TaskStatus?,
    ): Page<Task> = taskRepository.findAll(pageable, status)

    private fun submitToWorkerAsync(task: Task) {
        try {
            val result = imageProcessor.submitImage(task.imageUrl)
            val updated = task.withJobId(result.jobId)
            updated.transitionTo(TaskStatus.SUBMITTED)
            taskRepository.save(updated)
        } catch (e: Exception) {
            log.warn("Failed to submit task {} to worker, will retry via polling: {}", task.id, e.message)
        }
    }
}
