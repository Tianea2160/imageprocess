package com.example.imageprocess.application

import com.example.imageprocess.domain.exception.InvalidTaskUrlException
import com.example.imageprocess.domain.exception.TaskNotFoundException
import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.TaskEventPublisher
import com.example.imageprocess.domain.port.outbound.TaskRepository
import com.example.imageprocess.domain.port.outbound.TsidGenerator
import com.example.imageprocess.fixture.TaskFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class TaskServiceTest {
    private val taskRepository = mockk<TaskRepository>()
    private val taskEventPublisher = mockk<TaskEventPublisher>(relaxed = true)
    private val tsidGenerator = mockk<TsidGenerator>()

    private lateinit var taskService: TaskService

    @BeforeEach
    fun setUp() {
        taskService =
            TaskService(
                taskRepository = taskRepository,
                taskEventPublisher = taskEventPublisher,
                tsidGenerator = tsidGenerator,
                initialDelayMs = 1000,
                spreadWindowMs = 2000,
            )
    }

    @Test
    fun `createTask should save and publish submit event`() {
        val imageUrl = "https://example.com/image.png"
        every { tsidGenerator.generate() } returns "01H5N0640J7Q"
        every { taskRepository.findByFingerprint(any()) } returns null
        val taskSlot = slot<Task>()
        every { taskRepository.save(capture(taskSlot)) } answers { taskSlot.captured }

        val result = taskService.createTask(imageUrl)

        result.imageUrl shouldBe imageUrl
        result.state shouldBe TaskStatus.PENDING
        result.nextPollAt shouldNotBe null
        verify { taskRepository.save(any()) }
        verify { taskEventPublisher.publishSubmitTask(result.id, imageUrl) }
    }

    @Test
    fun `createTask should return existing task when fingerprint already exists`() {
        val imageUrl = "https://example.com/image.png"
        val existing = TaskFixture.create(imageUrl = imageUrl)
        every { taskRepository.findByFingerprint(any()) } returns existing

        val result = taskService.createTask(imageUrl)

        result shouldBe existing
        verify(exactly = 0) { taskRepository.save(any()) }
        verify(exactly = 0) { taskEventPublisher.publishSubmitTask(any(), any()) }
    }

    @Test
    fun `createTask should throw InvalidTaskUrlException for blank URL`() {
        shouldThrow<InvalidTaskUrlException> {
            taskService.createTask("")
        }
    }

    @Test
    fun `createTask should throw InvalidTaskUrlException for URL exceeding 2048 characters`() {
        val longUrl = "https://example.com/" + "a".repeat(2030)

        shouldThrow<InvalidTaskUrlException> {
            taskService.createTask(longUrl)
        }
    }

    @Test
    fun `createTask should throw InvalidTaskUrlException for non-HTTP URL`() {
        shouldThrow<InvalidTaskUrlException> {
            taskService.createTask("ftp://example.com/image.png")
        }
    }

    @Test
    fun `getTask should return task when found`() {
        val task = TaskFixture.create()
        every { taskRepository.findById("01H5N0640J7Q") } returns task

        val result = taskService.getTask("01H5N0640J7Q")

        result shouldBe task
    }

    @Test
    fun `getTask should throw TaskNotFoundException when not found`() {
        every { taskRepository.findById("nonexistent") } returns null

        shouldThrow<TaskNotFoundException> {
            taskService.getTask("nonexistent")
        }
    }

    @Test
    fun `listTasks should delegate to repository with pageable and status`() {
        val pageable = PageRequest.of(0, 20)
        val tasks = listOf(TaskFixture.create())
        val page = PageImpl(tasks, pageable, 1)
        every { taskRepository.findAll(pageable, TaskStatus.COMPLETED) } returns page

        val result = taskService.listTasks(pageable, TaskStatus.COMPLETED)

        result.totalElements shouldBe 1
        result.content[0] shouldBe tasks[0]
    }
}
