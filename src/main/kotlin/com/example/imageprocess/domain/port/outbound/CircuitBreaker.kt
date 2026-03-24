package com.example.imageprocess.domain.port.outbound

interface CircuitBreaker {
    fun isOpen(): Boolean

    fun recordSuccess()

    fun recordFailure()
}
