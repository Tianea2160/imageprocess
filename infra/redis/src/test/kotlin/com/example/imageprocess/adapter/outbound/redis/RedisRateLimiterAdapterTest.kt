package com.example.imageprocess.adapter.outbound.redis

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate

@SpringBootTest(
    classes = [RedisRateLimiterAdapter::class],
    properties = [
        "rate-limiter.max-tokens=3",
        "rate-limiter.refill-rate=1",
        "rate-limiter.refill-interval-ms=1000",
    ],
)
@Import(RedisTestConfig::class)
class RedisRateLimiterAdapterTest {
    @Autowired
    private lateinit var rateLimiter: RedisRateLimiterAdapter

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        redisTemplate.delete("rate_limiter:mock_worker")
    }

    @Test
    fun `should allow requests up to max tokens`() {
        rateLimiter.tryAcquire() shouldBe true
        rateLimiter.tryAcquire() shouldBe true
        rateLimiter.tryAcquire() shouldBe true
    }

    @Test
    fun `should reject when tokens exhausted`() {
        repeat(3) { rateLimiter.tryAcquire() }

        rateLimiter.tryAcquire() shouldBe false
    }

    @Test
    fun `should refill tokens after interval`() {
        repeat(3) { rateLimiter.tryAcquire() }
        rateLimiter.tryAcquire() shouldBe false

        Thread.sleep(1100)

        rateLimiter.tryAcquire() shouldBe true
    }

    @Test
    fun `should not exceed max tokens after long idle`() {
        repeat(3) { rateLimiter.tryAcquire() }

        Thread.sleep(3500)

        rateLimiter.tryAcquire() shouldBe true
        rateLimiter.tryAcquire() shouldBe true
        rateLimiter.tryAcquire() shouldBe true
        rateLimiter.tryAcquire() shouldBe false
    }
}
