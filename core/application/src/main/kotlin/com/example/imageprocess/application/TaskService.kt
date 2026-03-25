package com.example.imageprocess.application

import com.example.imageprocess.domain.exception.InvalidTaskUrlException
import com.example.imageprocess.domain.exception.TaskNotFoundException
import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.inbound.TaskUseCase
import com.example.imageprocess.domain.port.outbound.TaskEventPublisher
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
    private val taskEventPublisher: TaskEventPublisher,
    private val tsidGenerator: TsidGenerator,
    @Value("\${polling.initial-delay-ms}") private val initialDelayMs: Long,
    @Value("\${polling.spread-window-ms}") private val spreadWindowMs: Long,
) : TaskUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createTask(imageUrl: String): Task {
        if (imageUrl.isBlank()) throw InvalidTaskUrlException("imageUrl must not be blank")
        if (imageUrl.length > 2048) throw InvalidTaskUrlException("imageUrl must not exceed 2048 characters")
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            throw InvalidTaskUrlException("imageUrl must be an HTTP or HTTPS URL")
        }

        val fingerprint = Task.computeFingerprint(imageUrl)

        val existing = taskRepository.findByFingerprint(fingerprint)
        if (existing != null && !existing.state.isTerminal()) {
            return existing
        }

        val nextPollAt =
            Instant
                .now()
                .plus(Duration.ofMillis(initialDelayMs))
                .plus(Duration.ofMillis(Random.nextLong(0, spreadWindowMs)))

        val task = Task(id = tsidGenerator.generate(), imageUrl = imageUrl, nextPollAt = nextPollAt)
        val saved = taskRepository.save(task)

        taskEventPublisher.publishSubmitTask(saved.id, saved.imageUrl)

        return saved
    }

    @Transactional(readOnly = true)
    override fun getTask(taskId: String): Task =
        taskRepository.findById(taskId)
            ?: throw TaskNotFoundException(taskId)

    @Transactional(readOnly = true)
    override fun listTasks(
        pageable: Pageable,
        status: TaskStatus?,
    ): Page<Task> = taskRepository.findAll(pageable, status)
}
