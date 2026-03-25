package com.example.imageprocess.domain.port.outbound

enum class WorkerJobStatus {
    PROCESSING,
    COMPLETED,
    FAILED,
}

sealed class SubmitResult {
    data class Success(
        val jobId: String,
    ) : SubmitResult()

    data class NonRetryableFailure(
        val reason: String,
    ) : SubmitResult()

    data class RetryableFailure(
        val reason: String,
    ) : SubmitResult()
}

sealed class StatusResult {
    data class Success(
        val jobId: String,
        val status: WorkerJobStatus,
        val result: String?,
    ) : StatusResult()

    data class NonRetryableFailure(
        val reason: String,
    ) : StatusResult()

    data class RetryableFailure(
        val reason: String,
    ) : StatusResult()
}

interface ImageProcessor {
    fun submitImage(imageUrl: String): SubmitResult

    fun getJobStatus(jobId: String): StatusResult
}
