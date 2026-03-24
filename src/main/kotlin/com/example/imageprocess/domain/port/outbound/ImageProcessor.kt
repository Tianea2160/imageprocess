package com.example.imageprocess.domain.port.outbound

data class ProcessResult(
    val jobId: String,
    val status: String,
)

data class ProcessStatusResult(
    val jobId: String,
    val status: String,
    val result: String?,
)

interface ImageProcessor {
    fun submitImage(imageUrl: String): ProcessResult

    fun getJobStatus(jobId: String): ProcessStatusResult
}
