package com.example.imageprocess.application

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.CircuitBreaker
import com.example.imageprocess.domain.port.outbound.TaskEventPublisher
import com.example.imageprocess.domain.port.outbound.TaskRepository
import com.example.imageprocess.fixture.TaskFixture
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TaskPollingServiceTest {
    private val taskRepository = mockk<TaskRepository>()
    private val taskEventPublisher = mockk<TaskEventPublisher>(relaxed = true)
    private val taskPollExecutor = mockk<TaskPollExecutor>(relaxed = true)
    private val circuitBreaker = mockk<CircuitBreaker>()
    private val sm = TaskStateMachineConfig().taskStateMachine()

    private lateinit var service: TaskPollingService

    @BeforeEach
    fun setUp() {
        service =
            TaskPollingService(
                taskRepository = taskRepository,
                taskEventPublisher = taskEventPublisher,
                taskPollExecutor = taskPollExecutor,
                circuitBreaker = circuitBreaker,
                sm = sm,
                budgetPerTick = 10,
                baseDelayMs = 1000,
            )
        service.startPolling()
    }

    @Test
    fun `pollTasks should skip when not running`() =
        run {
            service.stopPolling()

            service.pollTasks()

            verify(exactly = 0) { circuitBreaker.isOpen() }
            verify(exactly = 0) { taskRepository.findPollableTasks(any(), any()) }
        }

    @Test
    fun `pollTasks should skip when circuit breaker is open`() =
        run {
            every { circuitBreaker.isOpen() } returns true

            service.pollTasks()

            verify(exactly = 0) { taskRepository.findPollableTasks(any(), any()) }
        }

    @Test
    fun `pollTasks should skip when no pollable tasks`() =
        run {
            every { circuitBreaker.isOpen() } returns false
            every { taskRepository.findPollableTasks(any(), any()) } returns emptyList()

            service.pollTasks()

            verify(exactly = 0) { taskPollExecutor.pollAndUpdateTask(any()) }
        }

    @Test
    fun `pollTasks should call pollAndUpdateTask for each task`() =
        run {
            val task1 = TaskFixture.submitted(id = "task-1")
            val task2 = TaskFixture.submitted(id = "task-2")
            every { circuitBreaker.isOpen() } returns false
            every { taskRepository.findPollableTasks(any(), any()) } returns listOf(task1, task2)

            service.pollTasks()

            verify(exactly = 1) { taskPollExecutor.pollAndUpdateTask(task1) }
            verify(exactly = 1) { taskPollExecutor.pollAndUpdateTask(task2) }
        }

    @Test
    fun `recoverOnStartup should republish submit event for PENDING tasks without jobId`() {
        val pendingTask = TaskFixture.create(id = "task-1")
        every { taskRepository.findByStatusIn(any()) } returns listOf(pendingTask)

        service.recoverOnStartup()

        verify { taskEventPublisher.publishSubmitTask("task-1", pendingTask.imageUrl) }
        verify(exactly = 0) { taskRepository.save(any()) }
    }

    @Test
    fun `recoverOnStartup should set nextPollAt for SUBMITTED tasks with jobId`() {
        val submittedTask = TaskFixture.submitted(id = "task-1", jobId = "job-1")
        every { taskRepository.findByStatusIn(any()) } returns listOf(submittedTask)
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        service.recoverOnStartup()

        saved.captured.nextPollAt shouldNotBe null
        saved.captured.state shouldBe TaskStatus.SUBMITTED
    }

    @Test
    fun `recoverOnStartup should transition RETRY_WAITING to SUBMITTED`() {
        val retryTask = TaskFixture.create(id = "task-1", state = TaskStatus.RETRY_WAITING, jobId = "job-1")
        every { taskRepository.findByStatusIn(any()) } returns listOf(retryTask)
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        service.recoverOnStartup()

        saved.captured.state shouldBe TaskStatus.SUBMITTED
        saved.captured.nextPollAt shouldNotBe null
    }
}
