package com.example.imageprocess.adapter.outbound.mockworker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(classes = [MockWorkerClient::class])
@Import(MockWorkerTestConfig::class)
class MockWorkerClientRetryIntegrationTest {
    companion object {
        val mockServer = MockWebServer()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            mockServer.start()
            registry.add("mock-worker.base-url") { mockServer.url("/").toString().trimEnd('/') }
            registry.add("mock-worker.api-key") { "test-api-key" }
            registry.add("mock-worker.connect-timeout") { "1s" }
            registry.add("mock-worker.read-timeout") { "2s" }
            registry.add("mock-worker.max-retries") { "2" }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            mockServer.shutdown()
        }
    }

    @Autowired
    private lateinit var client: MockWorkerClient

    @AfterEach
    fun drainRequests() {
        while (mockServer.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) != null) {
            // drain
        }
    }

    private fun enqueueJson(statusCode: Int, body: String) {
        mockServer.enqueue(
            MockResponse.Builder()
                .code(statusCode)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    private fun enqueueError(statusCode: Int, detail: String = "error") {
        enqueueJson(statusCode, """{"detail":"$detail"}""")
    }

    private fun enqueueEmpty(statusCode: Int) {
        mockServer.enqueue(
            MockResponse.Builder()
                .code(statusCode)
                .addHeader("Content-Type", "application/json")
                .build(),
        )
    }

    private fun successProcessResponse(jobId: String = "job-1") =
        """{"jobId":"$jobId","status":"PROCESSING"}"""

    private fun successStatusResponse(
        jobId: String = "job-1",
        status: String = "PROCESSING",
        result: String? = null,
    ): String {
        val resultField = if (result != null) """"$result"""" else "null"
        return """{"jobId":"$jobId","status":"$status","result":$resultField}"""
    }

    // ========== submitImage ==========

    @Test
    fun `submitImage should return response on 200 success`() {
        enqueueJson(200, successProcessResponse("job-123"))

        val result = client.submitImage("https://example.com/img.png")

        result.jobId shouldBe "job-123"
        result.status shouldBe "PROCESSING"
    }

    @Test
    fun `submitImage should throw NonRetryableWorkerException on 400`() {
        enqueueError(400, "bad request")

        val ex = assertThrows<NonRetryableWorkerException> {
            client.submitImage("https://example.com/img.png")
        }
        ex.message shouldContain "bad request"
    }

    @Test
    fun `submitImage should throw NonRetryableWorkerException on 401`() {
        enqueueError(401, "unauthorized")

        assertThrows<NonRetryableWorkerException> {
            client.submitImage("https://example.com/img.png")
        }
    }

    @Test
    fun `submitImage should throw NonRetryableWorkerException on 404`() {
        enqueueError(404, "not found")

        assertThrows<NonRetryableWorkerException> {
            client.submitImage("https://example.com/img.png")
        }
    }

    @Test
    fun `submitImage should throw NonRetryableWorkerException on 422`() {
        enqueueError(422, "unprocessable")

        val ex = assertThrows<NonRetryableWorkerException> {
            client.submitImage("https://example.com/img.png")
        }
        ex.message shouldContain "unprocessable"
    }

    @Test
    fun `submitImage should not retry on non-retryable status codes`() {
        enqueueError(400, "bad request")

        assertThrows<NonRetryableWorkerException> {
            client.submitImage("https://example.com/img.png")
        }

        mockServer.requestCount shouldBe 1
    }

    @Test
    fun `submitImage should retry on 500 and succeed on second attempt`() {
        val before = mockServer.requestCount
        enqueueError(500, "internal error")
        enqueueJson(200, successProcessResponse("job-retry"))

        val result = client.submitImage("https://example.com/img.png")

        result.jobId shouldBe "job-retry"
        (mockServer.requestCount - before) shouldBe 2
    }

    @Test
    fun `submitImage should retry twice on 502 and succeed on third attempt`() {
        val before = mockServer.requestCount
        enqueueError(502, "bad gateway")
        enqueueError(502, "bad gateway")
        enqueueJson(200, successProcessResponse("job-ok"))

        val result = client.submitImage("https://example.com/img.png")

        result.jobId shouldBe "job-ok"
        (mockServer.requestCount - before) shouldBe 3
    }

    @Test
    fun `submitImage should throw RetryableWorkerException after exhausting retries on 503`() {
        val before = mockServer.requestCount
        repeat(3) { enqueueError(503, "service unavailable") }

        assertThrows<RetryableWorkerException> {
            client.submitImage("https://example.com/img.png")
        }

        (mockServer.requestCount - before) shouldBe 3
    }

    @Test
    fun `submitImage should stop retrying when NonRetryableWorkerException occurs during retry`() {
        val before = mockServer.requestCount
        enqueueError(500, "server error")
        enqueueError(400, "bad request")

        assertThrows<NonRetryableWorkerException> {
            client.submitImage("https://example.com/img.png")
        }

        (mockServer.requestCount - before) shouldBe 2
    }

    @Test
    fun `submitImage should throw RetryableWorkerException on empty response body`() {
        val before = mockServer.requestCount
        repeat(3) { enqueueEmpty(200) }

        assertThrows<RetryableWorkerException> {
            client.submitImage("https://example.com/img.png")
        }

        (mockServer.requestCount - before) shouldBe 3
    }

    @Test
    fun `submitImage should send correct request headers and body`() {
        enqueueJson(200, successProcessResponse())

        client.submitImage("https://example.com/test-image.png")

        val request = mockServer.takeRequest()
        request.getHeader("X-API-KEY") shouldBe "test-api-key"
        request.getHeader("Content-Type") shouldContain "application/json"
        request.body.readUtf8() shouldContain "test-image.png"
        request.path shouldBe "/process"
        request.method shouldBe "POST"
    }

    // ========== getJobStatus ==========

    @Test
    fun `getJobStatus should return PROCESSING status`() {
        enqueueJson(200, successStatusResponse(status = "PROCESSING"))

        val result = client.getJobStatus("job-1")

        result.jobId shouldBe "job-1"
        result.status shouldBe "PROCESSING"
        result.result shouldBe null
    }

    @Test
    fun `getJobStatus should return COMPLETED status with result`() {
        enqueueJson(200, successStatusResponse(status = "COMPLETED", result = "https://cdn/result.png"))

        val result = client.getJobStatus("job-1")

        result.status shouldBe "COMPLETED"
        result.result shouldBe "https://cdn/result.png"
    }

    @Test
    fun `getJobStatus should return FAILED status`() {
        enqueueJson(200, successStatusResponse(status = "FAILED"))

        val result = client.getJobStatus("job-1")

        result.status shouldBe "FAILED"
    }

    @Test
    fun `getJobStatus should throw NonRetryableWorkerException on 404`() {
        enqueueError(404, "not found")

        assertThrows<NonRetryableWorkerException> {
            client.getJobStatus("nonexistent-job")
        }

        mockServer.requestCount shouldBe 1
    }

    @Test
    fun `getJobStatus should retry on 500 and succeed`() {
        val before = mockServer.requestCount
        enqueueError(500, "internal error")
        enqueueJson(200, successStatusResponse())

        val result = client.getJobStatus("job-1")

        result.jobId shouldBe "job-1"
        (mockServer.requestCount - before) shouldBe 2
    }

    @Test
    fun `getJobStatus should throw RetryableWorkerException after exhausting retries on 503`() {
        val before = mockServer.requestCount
        repeat(3) { enqueueError(503, "service unavailable") }

        assertThrows<RetryableWorkerException> {
            client.getJobStatus("job-1")
        }

        (mockServer.requestCount - before) shouldBe 3
    }

    @Test
    fun `getJobStatus should throw RetryableWorkerException on empty response body`() {
        val before = mockServer.requestCount
        repeat(3) { enqueueEmpty(200) }

        assertThrows<RetryableWorkerException> {
            client.getJobStatus("job-1")
        }

        (mockServer.requestCount - before) shouldBe 3
    }

    @Test
    fun `getJobStatus should send correct request path`() {
        enqueueJson(200, successStatusResponse())

        client.getJobStatus("job-abc")

        val request = mockServer.takeRequest()
        request.path shouldBe "/process/job-abc"
        request.method shouldBe "GET"
        request.getHeader("X-API-KEY") shouldBe "test-api-key"
    }
}
