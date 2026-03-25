package com.example.imageprocess.adapter.outbound.mockworker

import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.StatusResult
import com.example.imageprocess.domain.port.outbound.SubmitResult
import com.example.imageprocess.domain.port.outbound.WorkerJobStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MockWorkerAdapter(
    private val client: MockWorkerClient,
) : ImageProcessor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun submitImage(imageUrl: String): SubmitResult =
        try {
            val response = client.submitImage(imageUrl)
            SubmitResult.Success(jobId = response.jobId)
        } catch (e: NonRetryableWorkerException) {
            SubmitResult.NonRetryableFailure(e.message ?: "Unknown")
        } catch (e: RateLimitedWorkerException) {
            log.warn("submitImage rate limited, retry-after: {}s", e.retryAfterSeconds)
            SubmitResult.RetryableFailure("Rate limited: ${e.message}", e.retryAfterSeconds)
        } catch (e: RetryableWorkerException) {
            log.warn("submitImage failed after retries: {}", e.message)
            SubmitResult.RetryableFailure("submitImage failed after retries: ${e.message}")
        }

    override fun getJobStatus(jobId: String): StatusResult =
        try {
            val response = client.getJobStatus(jobId)
            StatusResult.Success(
                jobId = response.jobId,
                status = WorkerJobStatus.valueOf(response.status),
                result = response.result,
            )
        } catch (e: NonRetryableWorkerException) {
            StatusResult.NonRetryableFailure(e.message ?: "Unknown")
        } catch (e: RateLimitedWorkerException) {
            log.warn("getJobStatus rate limited, retry-after: {}s", e.retryAfterSeconds)
            StatusResult.RetryableFailure("Rate limited: ${e.message}", e.retryAfterSeconds)
        } catch (e: RetryableWorkerException) {
            log.warn("getJobStatus failed after retries: {}", e.message)
            StatusResult.RetryableFailure("getJobStatus failed after retries: ${e.message}")
        }
}

data class MockWorkerRequest(
    val imageUrl: String,
)

data class MockWorkerProcessResponse(
    val jobId: String,
    val status: String,
)

data class MockWorkerStatusResponse(
    val jobId: String,
    val status: String,
    val result: String?,
)
