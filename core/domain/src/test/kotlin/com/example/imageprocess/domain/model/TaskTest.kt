package com.example.imageprocess.domain.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class TaskTest {
    @Test
    fun `should create task with PENDING state`() {
        val task = Task(id = "01H5N0640J7Q", imageUrl = "https://example.com/image.png")
        task.state shouldBe TaskStatus.PENDING
        task.id shouldNotBe null
        task.fingerprint shouldNotBe null
    }

    @Test
    fun `should compute consistent fingerprint for same URL`() {
        val fp1 = Task.computeFingerprint("https://example.com/image.png")
        val fp2 = Task.computeFingerprint("https://example.com/image.png")
        fp1 shouldBe fp2
    }

    @Test
    fun `should compute different fingerprint for different URLs`() {
        val fp1 = Task.computeFingerprint("https://example.com/image1.png")
        val fp2 = Task.computeFingerprint("https://example.com/image2.png")
        fp1 shouldNotBe fp2
    }

    @Test
    fun `withState should change state`() {
        val task = Task(id = "01H5N0640J7Q", imageUrl = "https://example.com/image.png")
        val updated = task.withState(TaskStatus.SUBMITTED)
        updated.state shouldBe TaskStatus.SUBMITTED
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
        retried.retryCount shouldBe 1
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
        polled.pollCount shouldBe 1
    }
}
