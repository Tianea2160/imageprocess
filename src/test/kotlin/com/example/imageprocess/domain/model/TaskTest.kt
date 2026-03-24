package com.example.imageprocess.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TaskTest {
    @Test
    fun `should create task with PENDING status`() {
        val task = Task(id = "01H5N0640J7Q", imageUrl = "https://example.com/image.png")
        assertEquals(TaskStatus.PENDING, task.status)
        assertNotNull(task.id)
        assertNotNull(task.fingerprint)
    }

    @Test
    fun `should compute consistent fingerprint for same URL`() {
        val fp1 = Task.computeFingerprint("https://example.com/image.png")
        val fp2 = Task.computeFingerprint("https://example.com/image.png")
        assertEquals(fp1, fp2)
    }

    @Test
    fun `should compute different fingerprint for different URLs`() {
        val fp1 = Task.computeFingerprint("https://example.com/image1.png")
        val fp2 = Task.computeFingerprint("https://example.com/image2.png")
        assert(fp1 != fp2)
    }

    @Test
    fun `should transition from PENDING to SUBMITTED`() {
        val task = Task(id = "01H5N0640J7Q", imageUrl = "https://example.com/image.png")
        task.transitionTo(TaskStatus.SUBMITTED)
        assertEquals(TaskStatus.SUBMITTED, task.status)
    }

    @Test
    fun `should throw on invalid transition`() {
        val task = Task(id = "01H5N0640J7Q", imageUrl = "https://example.com/image.png")
        task.transitionTo(TaskStatus.SUBMITTED)
        task.transitionTo(TaskStatus.PROCESSING)
        task.transitionTo(TaskStatus.COMPLETED)
        assertThrows<IllegalArgumentException> {
            task.transitionTo(TaskStatus.PENDING)
        }
    }

    @Test
    fun `should not allow transition from terminal state`() {
        val task = Task(id = "01H5N0640J7Q", imageUrl = "https://example.com/image.png")
        task.transitionTo(TaskStatus.FAILED)
        assertThrows<IllegalArgumentException> {
            task.transitionTo(TaskStatus.SUBMITTED)
        }
    }

    @Test
    fun `withRetry should increment retry count`() {
        val task = Task(id = "01H5N0640J7Q", imageUrl = "https://example.com/image.png")
        val retried =
            task.withRetry(
                java.time.Instant
                    .now()
                    .plusSeconds(10),
            )
        assertEquals(1, retried.retryCount)
    }

    @Test
    fun `withNextPoll should increment poll count`() {
        val task = Task(id = "01H5N0640J7Q", imageUrl = "https://example.com/image.png")
        val polled =
            task.withNextPoll(
                java.time.Instant
                    .now()
                    .plusSeconds(5),
            )
        assertEquals(1, polled.pollCount)
    }
}
