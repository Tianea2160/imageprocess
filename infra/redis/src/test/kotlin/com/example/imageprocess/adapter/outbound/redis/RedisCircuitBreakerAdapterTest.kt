package com.example.imageprocess.adapter.outbound.redis

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate

@SpringBootTest(
    classes = [RedisCircuitBreakerAdapter::class],
    properties = [
        "circuit-breaker.failure-threshold=3",
        "circuit-breaker.cooldown-seconds=2",
        "circuit-breaker.half-open-max-calls=1",
    ],
)
@Import(RedisTestConfig::class)
class RedisCircuitBreakerAdapterTest {
    @Autowired
    private lateinit var circuitBreaker: RedisCircuitBreakerAdapter

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        redisTemplate.delete(
            listOf(
                "circuit_breaker:mock_worker:failure_count",
                "circuit_breaker:mock_worker:open_until",
                "circuit_breaker:mock_worker:half_open_calls",
            ),
        )
    }

    @Test
    fun `should be closed initially`() {
        circuitBreaker.isOpen() shouldBe false
    }

    @Test
    fun `should stay closed below failure threshold`() {
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()

        circuitBreaker.isOpen() shouldBe false
    }

    @Test
    fun `should open after reaching failure threshold`() {
        repeat(3) { circuitBreaker.recordFailure() }

        circuitBreaker.isOpen() shouldBe true
    }

    @Test
    fun `should transition to half-open after cooldown`() {
        repeat(3) { circuitBreaker.recordFailure() }
        circuitBreaker.isOpen() shouldBe true

        Thread.sleep(2100)

        // half-open: first call allowed (within halfOpenMaxCalls=1)
        circuitBreaker.isOpen() shouldBe false
    }

    @Test
    fun `should limit calls in half-open state`() {
        repeat(3) { circuitBreaker.recordFailure() }

        Thread.sleep(2100)

        // first call allowed (half-open)
        circuitBreaker.isOpen() shouldBe false
        // second call rejected (exceeds halfOpenMaxCalls=1)
        circuitBreaker.isOpen() shouldBe true
    }

    @Test
    fun `should close after success in half-open`() {
        repeat(3) { circuitBreaker.recordFailure() }

        Thread.sleep(2100)

        // half-open: first call allowed
        circuitBreaker.isOpen() shouldBe false
        // success resets the circuit
        circuitBreaker.recordSuccess()

        circuitBreaker.isOpen() shouldBe false
        circuitBreaker.isOpen() shouldBe false
    }

    @Test
    fun `should re-open if failure in half-open reaches threshold`() {
        repeat(3) { circuitBreaker.recordFailure() }

        Thread.sleep(2100)

        // half-open
        circuitBreaker.isOpen() shouldBe false
        // failure during half-open
        repeat(3) { circuitBreaker.recordFailure() }

        circuitBreaker.isOpen() shouldBe true
    }

    @Test
    fun `openForSeconds should open circuit for given duration`() {
        circuitBreaker.openForSeconds(2)

        circuitBreaker.isOpen() shouldBe true

        Thread.sleep(2100)

        circuitBreaker.isOpen() shouldBe false
    }

    @Test
    fun `openForSeconds should reset failure count`() {
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()

        circuitBreaker.openForSeconds(2)

        Thread.sleep(2100)

        // half-open: first call allowed
        circuitBreaker.isOpen() shouldBe false
        // success closes the circuit, resetting everything
        circuitBreaker.recordSuccess()

        // failure count was reset, so 2 more failures should not re-open
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        circuitBreaker.isOpen() shouldBe false
    }

    @Test
    fun `recordSuccess should fully reset circuit`() {
        repeat(3) { circuitBreaker.recordFailure() }
        circuitBreaker.isOpen() shouldBe true

        circuitBreaker.recordSuccess()

        circuitBreaker.isOpen() shouldBe false
        // failures should start from 0 again
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        circuitBreaker.isOpen() shouldBe false
    }
}
