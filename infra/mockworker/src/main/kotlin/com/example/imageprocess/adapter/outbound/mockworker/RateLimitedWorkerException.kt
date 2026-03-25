package com.example.imageprocess.adapter.outbound.mockworker

class RateLimitedWorkerException(
    message: String,
    val retryAfterSeconds: Long?,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
