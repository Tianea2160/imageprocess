package com.example.imageprocess.application

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.CircuitBreaker
import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.RateLimiter
import com.example.imageprocess.domain.port.outbound.StatusResult
import com.example.imageprocess.domain.port.outbound.TaskRepository
import com.example.imageprocess.domain.port.outbound.WorkerJobStatus
import com.example.imageprocess.fixture.TaskFixture
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TaskPollExecutorTest {
    private val taskRepository = mockk<TaskRepository>()
    private val imageProcessor = mockk<ImageProcessor>()
    private val rateLimiter = mockk<RateLimiter>()
    private val circuitBreaker = mockk<CircuitBreaker>(relaxed = true)
    private val sm = TaskStateMachineConfig().taskStateMachine()

    private lateinit var executor: TaskPollExecutor

    @BeforeEach
    fun setUp() {
        executor =
            TaskPollExecutor(
                taskRepository = taskRepository,
                imageProcessor = imageProcessor,
                rateLimiter = rateLimiter,
                circuitBreaker = circuitBreaker,
                sm = sm,
                baseDelayMs = 1000,
                maxDelayMs = 30000,
                maxRetryCount = 3,
            )
    }

    @Test
    fun `should skip when rate limiter rejects`() {
        val task = TaskFixture.create(state = TaskStatus.SUBMITTED, jobId = "job-1")
        every { rateLimiter.tryAcquire() } returns false

        executor.pollAndUpdateTask(task)

        verify(exactly = 0) { imageProcessor.getJobStatus(any()) }
        verify(exactly = 0) { taskRepository.save(any()) }
    }

    @Test
    fun `should skip when task has no jobId`() {
        val task = TaskFixture.create(state = TaskStatus.SUBMITTED)
        every { rateLimiter.tryAcquire() } returns true

        executor.pollAndUpdateTask(task)

        verify(exactly = 0) { imageProcessor.getJobStatus(any()) }
    }

    @Test
    fun `should transition to COMPLETED on successful completion`() {
        val task = TaskFixture.processing(jobId = "job-1")
        every { rateLimiter.tryAcquire() } returns true
        every { imageProcessor.getJobStatus("job-1") } returns
            StatusResult.Success(jobId = "job-1", status = WorkerJobStatus.COMPLETED, result = "done")
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        executor.pollAndUpdateTask(task)

        saved.captured.state shouldBe TaskStatus.COMPLETED
        saved.captured.result shouldBe "done"
        saved.captured.nextPollAt shouldBe null
        verify { circuitBreaker.recordSuccess() }
    }

    @Test
    fun `should transition to FAILED on worker failure`() {
        val task = TaskFixture.processing(jobId = "job-1")
        every { rateLimiter.tryAcquire() } returns true
        every { imageProcessor.getJobStatus("job-1") } returns
            StatusResult.Success(jobId = "job-1", status = WorkerJobStatus.FAILED, result = null)
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        executor.pollAndUpdateTask(task)

        saved.captured.state shouldBe TaskStatus.FAILED
        saved.captured.failReason shouldBe "Mock Worker returned FAILED"
        saved.captured.nextPollAt shouldBe null
    }

    @Test
    fun `should transition to PROCESSING and set nextPollAt on PROCESSING result`() {
        val task = TaskFixture.create(state = TaskStatus.SUBMITTED, jobId = "job-1")
        every { rateLimiter.tryAcquire() } returns true
        every { imageProcessor.getJobStatus("job-1") } returns
            StatusResult.Success(jobId = "job-1", status = WorkerJobStatus.PROCESSING, result = null)
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        executor.pollAndUpdateTask(task)

        saved.captured.state shouldBe TaskStatus.PROCESSING
        saved.captured.nextPollAt shouldNotBe null
    }

    @Test
    fun `should not re-transition when already PROCESSING`() {
        val task = TaskFixture.create(state = TaskStatus.PROCESSING, jobId = "job-1")
        every { rateLimiter.tryAcquire() } returns true
        every { imageProcessor.getJobStatus("job-1") } returns
            StatusResult.Success(jobId = "job-1", status = WorkerJobStatus.PROCESSING, result = null)
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        executor.pollAndUpdateTask(task)

        saved.captured.state shouldBe TaskStatus.PROCESSING
        verify { taskRepository.save(any()) }
    }

    @Test
    fun `should transition to FAILED on non-retryable failure`() {
        val task = TaskFixture.create(state = TaskStatus.SUBMITTED, jobId = "job-1")
        every { rateLimiter.tryAcquire() } returns true
        every { imageProcessor.getJobStatus("job-1") } returns
            StatusResult.NonRetryableFailure(reason = "Bad request")
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        executor.pollAndUpdateTask(task)

        saved.captured.state shouldBe TaskStatus.FAILED
        saved.captured.failReason shouldBe "Bad request"
    }

    @Test
    fun `should transition to RETRY_WAITING on retryable failure`() {
        val task = TaskFixture.create(state = TaskStatus.SUBMITTED, jobId = "job-1")
        every { rateLimiter.tryAcquire() } returns true
        every { imageProcessor.getJobStatus("job-1") } returns
            StatusResult.RetryableFailure(reason = "Timeout")
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        executor.pollAndUpdateTask(task)

        saved.captured.state shouldBe TaskStatus.RETRY_WAITING
        saved.captured.retryCount shouldBe 1
        verify { circuitBreaker.recordFailure() }
    }

    @Test
    fun `should transition to FAILED when max retry count exceeded`() {
        val task = TaskFixture.create(state = TaskStatus.SUBMITTED, jobId = "job-1", retryCount = 3)
        every { rateLimiter.tryAcquire() } returns true
        every { imageProcessor.getJobStatus("job-1") } returns
            StatusResult.RetryableFailure(reason = "Timeout")
        val saved = slot<Task>()
        every { taskRepository.save(capture(saved)) } answers { saved.captured }

        executor.pollAndUpdateTask(task)

        saved.captured.state shouldBe TaskStatus.FAILED
        saved.captured.failReason shouldBe "Max retry count exceeded"
    }
}
