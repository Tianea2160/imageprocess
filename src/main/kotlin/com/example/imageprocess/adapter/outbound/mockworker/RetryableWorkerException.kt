package com.example.imageprocess.adapter.outbound.mockworker

class RetryableWorkerException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
