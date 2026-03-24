package com.example.imageprocess.adapter.outbound.redis

import com.example.imageprocess.domain.port.outbound.CircuitBreaker
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class RedisCircuitBreakerAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${circuit-breaker.failure-threshold}") private val failureThreshold: Int,
    @Value("\${circuit-breaker.cooldown-seconds}") private val cooldownSeconds: Long,
    @Value("\${circuit-breaker.half-open-max-calls}") private val halfOpenMaxCalls: Int,
) : CircuitBreaker {
    companion object {
        private const val KEY_PREFIX = "circuit_breaker:mock_worker"
        private const val FAILURE_COUNT_KEY = "$KEY_PREFIX:failure_count"
        private const val OPEN_UNTIL_KEY = "$KEY_PREFIX:open_until"
        private const val HALF_OPEN_CALLS_KEY = "$KEY_PREFIX:half_open_calls"
    }

    override fun isOpen(): Boolean {
        val openUntil = redisTemplate.opsForValue().get(OPEN_UNTIL_KEY) ?: return false
        val openUntilInstant = Instant.ofEpochMilli(openUntil.toLong())
        if (Instant.now().isAfter(openUntilInstant)) {
            // half-open: 제한된 요청만 허용
            val halfOpenCalls = redisTemplate.opsForValue().increment(HALF_OPEN_CALLS_KEY) ?: 1
            if (halfOpenCalls == 1L) {
                redisTemplate.expire(HALF_OPEN_CALLS_KEY, Duration.ofSeconds(cooldownSeconds))
            }
            return halfOpenCalls > halfOpenMaxCalls
        }
        return true
    }

    override fun recordSuccess() {
        redisTemplate.delete(listOf(FAILURE_COUNT_KEY, OPEN_UNTIL_KEY, HALF_OPEN_CALLS_KEY))
    }

    override fun recordFailure() {
        val failures = redisTemplate.opsForValue().increment(FAILURE_COUNT_KEY) ?: 1
        redisTemplate.expire(FAILURE_COUNT_KEY, Duration.ofSeconds(cooldownSeconds * 2))

        if (failures >= failureThreshold) {
            val openUntil = Instant.now().plusSeconds(cooldownSeconds).toEpochMilli()
            redisTemplate.opsForValue().set(
                OPEN_UNTIL_KEY,
                openUntil.toString(),
                Duration.ofSeconds(cooldownSeconds * 2),
            )
            redisTemplate.delete(FAILURE_COUNT_KEY)
        }
    }
}
