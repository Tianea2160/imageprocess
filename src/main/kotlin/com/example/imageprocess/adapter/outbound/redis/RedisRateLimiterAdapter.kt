package com.example.imageprocess.adapter.outbound.redis

import com.example.imageprocess.domain.port.outbound.RateLimiter
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

@Component
class RedisRateLimiterAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${rate-limiter.max-tokens}") private val maxTokens: Long,
    @Value("\${rate-limiter.refill-rate}") private val refillRate: Long,
    @Value("\${rate-limiter.refill-interval-ms}") private val refillIntervalMs: Long,
) : RateLimiter {
    companion object {
        private const val KEY = "rate_limiter:mock_worker"

        private val TOKEN_BUCKET_SCRIPT =
            RedisScript.of(
                """
                local key = KEYS[1]
                local max_tokens = tonumber(ARGV[1])
                local refill_rate = tonumber(ARGV[2])
                local refill_interval_ms = tonumber(ARGV[3])
                local now = tonumber(ARGV[4])

                local data = redis.call('HMGET', key, 'tokens', 'last_refill')
                local tokens = tonumber(data[1])
                local last_refill = tonumber(data[2])

                if tokens == nil then
                    tokens = max_tokens
                    last_refill = now
                end

                local elapsed = now - last_refill
                local refills = math.floor(elapsed / refill_interval_ms)
                if refills > 0 then
                    tokens = math.min(max_tokens, tokens + refills * refill_rate)
                    last_refill = last_refill + refills * refill_interval_ms
                end

                local allowed = 0
                if tokens > 0 then
                    tokens = tokens - 1
                    allowed = 1
                end

                redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
                redis.call('PEXPIRE', key, refill_interval_ms * math.ceil(max_tokens / refill_rate) * 2)

                return allowed
                """.trimIndent(),
                Long::class.java,
            )
    }

    override fun tryAcquire(): Boolean {
        val now = System.currentTimeMillis()
        val result =
            redisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                listOf(KEY),
                maxTokens.toString(),
                refillRate.toString(),
                refillIntervalMs.toString(),
                now.toString(),
            )
        return result == 1L
    }
}
