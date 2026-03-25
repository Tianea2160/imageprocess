package com.example.imageprocess.adapter.outbound.mockworker

class NonRetryableWorkerException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
