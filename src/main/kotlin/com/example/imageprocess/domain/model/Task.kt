package com.example.imageprocess.domain.model

import com.example.imageprocess.domain.exception.InvalidTaskStateTransitionException
import java.security.MessageDigest
import java.time.Instant

class Task(
    val id: String,
    val imageUrl: String,
    val fingerprint: String = computeFingerprint(imageUrl),
    status: TaskStatus = TaskStatus.PENDING,
    val jobId: String? = null,
    val result: String? = null,
    val failReason: String? = null,
    val retryCount: Int = 0,
    val pollCount: Int = 0,
    val nextPollAt: Instant? = null,
    val version: Long = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    var status: TaskStatus = status
        private set

    fun transitionTo(newStatus: TaskStatus) {
        if (!TaskStatus.canTransition(status, newStatus)) {
            throw InvalidTaskStateTransitionException(status, newStatus)
        }
        status = newStatus
    }

    fun withJobId(jobId: String): Task =
        Task(
            id = id,
            imageUrl = imageUrl,
            fingerprint = fingerprint,
            status = status,
            jobId = jobId,
            result = result,
            failReason = failReason,
            retryCount = retryCount,
            pollCount = pollCount,
            nextPollAt = nextPollAt,
            version = version,
            createdAt = createdAt,
            updatedAt = Instant.now(),
        )

    fun withResult(result: String): Task =
        Task(
            id = id,
            imageUrl = imageUrl,
            fingerprint = fingerprint,
            status = status,
            jobId = jobId,
            result = result,
            failReason = failReason,
            retryCount = retryCount,
            pollCount = pollCount,
            nextPollAt = nextPollAt,
            version = version,
            createdAt = createdAt,
            updatedAt = Instant.now(),
        )

    fun withFailReason(reason: String): Task =
        Task(
            id = id,
            imageUrl = imageUrl,
            fingerprint = fingerprint,
            status = status,
            jobId = jobId,
            result = result,
            failReason = reason,
            retryCount = retryCount,
            pollCount = pollCount,
            nextPollAt = nextPollAt,
            version = version,
            createdAt = createdAt,
            updatedAt = Instant.now(),
        )

    fun withRetry(nextPollAt: Instant): Task =
        Task(
            id = id,
            imageUrl = imageUrl,
            fingerprint = fingerprint,
            status = status,
            jobId = jobId,
            result = result,
            failReason = failReason,
            retryCount = retryCount + 1,
            pollCount = pollCount,
            nextPollAt = nextPollAt,
            version = version,
            createdAt = createdAt,
            updatedAt = Instant.now(),
        )

    fun withNextPoll(nextPollAt: Instant?): Task =
        Task(
            id = id,
            imageUrl = imageUrl,
            fingerprint = fingerprint,
            status = status,
            jobId = jobId,
            result = result,
            failReason = failReason,
            retryCount = retryCount,
            pollCount = pollCount + 1,
            nextPollAt = nextPollAt,
            version = version,
            createdAt = createdAt,
            updatedAt = Instant.now(),
        )

    companion object {
        fun computeFingerprint(imageUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest
                .digest(imageUrl.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
