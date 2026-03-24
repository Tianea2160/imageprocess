package com.example.imageprocess.domain.port.outbound

interface RateLimiter {
    fun tryAcquire(): Boolean
}
