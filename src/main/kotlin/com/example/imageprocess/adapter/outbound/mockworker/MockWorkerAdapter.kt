package com.example.imageprocess.adapter.outbound.mockworker

import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.StatusResult
import com.example.imageprocess.domain.port.outbound.SubmitResult
import com.example.imageprocess.domain.port.outbound.WorkerJobStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

@Component
class MockWorkerAdapter(
    @Value("\${mock-worker.base-url}") private val baseUrl: String,
    @Value("\${mock-worker.api-key}") private val apiKey: String,
    @Value("\${mock-worker.connect-timeout}") private val connectTimeout: Duration,
    @Value("\${mock-worker.read-timeout}") private val readTimeout: Duration,
    @Value("\${mock-worker.max-retries:3}") private val maxRetries: Int,
    private val objectMapper: ObjectMapper,
) : ImageProcessor {
    private val log = LoggerFactory.getLogger(javaClass)

    private val nonRetryableStatusCodes = setOf(400, 401, 404, 422)

    private val restClient: RestClient by lazy {
        val httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build()
        val requestFactory =
            JdkClientHttpRequestFactory(httpClient).apply {
                setReadTimeout(readTimeout)
            }
        RestClient
            .builder()
            .requestFactory(requestFactory)
            .baseUrl(baseUrl)
            .defaultHeader("X-API-KEY", apiKey)
            .build()
    }

    override fun submitImage(imageUrl: String): SubmitResult =
        withRetry("submitImage", ::toSubmitFailure) {
            val response =
                restClient
                    .post()
                    .uri("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(MockWorkerRequest(imageUrl))
                    .retrieve()
                    .body<MockWorkerProcessResponse>()
                    ?: return@withRetry SubmitResult.RetryableFailure("Empty response from Mock Worker")

            SubmitResult.Success(jobId = response.jobId)
        }

    override fun getJobStatus(jobId: String): StatusResult =
        withRetry("getJobStatus", ::toStatusFailure) {
            val response =
                restClient
                    .get()
                    .uri("/process/{jobId}", jobId)
                    .retrieve()
                    .body<MockWorkerStatusResponse>()
                    ?: return@withRetry StatusResult.RetryableFailure("Empty response from Mock Worker")

            StatusResult.Success(
                jobId = response.jobId,
                status = WorkerJobStatus.valueOf(response.status),
                result = response.result,
            )
        }

    private fun <T> withRetry(
        operationName: String,
        toFailure: (retryable: Boolean, reason: String) -> T,
        action: () -> T,
    ): T {
        var lastReason = ""

        repeat(maxRetries) { attempt ->
            try {
                return action()
            } catch (e: RestClientResponseException) {
                val statusCode = e.statusCode.value()
                val detail = parseErrorDetail(e.responseBodyAsString)

                if (statusCode in nonRetryableStatusCodes) {
                    log.warn("{} rejected with non-retryable status {}: {}", operationName, statusCode, detail)
                    return toFailure(false, detail)
                }

                lastReason = "Mock Worker returned $statusCode: $detail"
                log.warn("{} attempt {}/{} failed: {}", operationName, attempt + 1, maxRetries, lastReason)
                sleepBeforeRetry(attempt)
            } catch (e: Exception) {
                lastReason = e.message ?: "Unknown"
                log.warn("{} attempt {}/{} failed: {}", operationName, attempt + 1, maxRetries, lastReason)
                sleepBeforeRetry(attempt)
            }
        }

        log.warn("{} failed after {} retries: {}", operationName, maxRetries, lastReason)
        return toFailure(true, "$operationName failed after $maxRetries retries: $lastReason")
    }

    private fun toSubmitFailure(
        retryable: Boolean,
        reason: String,
    ): SubmitResult = if (retryable) SubmitResult.RetryableFailure(reason) else SubmitResult.NonRetryableFailure(reason)

    private fun toStatusFailure(
        retryable: Boolean,
        reason: String,
    ): StatusResult = if (retryable) StatusResult.RetryableFailure(reason) else StatusResult.NonRetryableFailure(reason)

    private fun sleepBeforeRetry(attempt: Int) {
        if (attempt < maxRetries - 1) {
            val delayMs = 100L * (1 shl attempt) // 100ms, 200ms, 400ms
            Thread.sleep(delayMs)
        }
    }

    private fun parseErrorDetail(body: String): String =
        try {
            val node = objectMapper.readTree(body)
            node.get("detail")?.asString() ?: body
        } catch (_: Exception) {
            body
        }
}

data class MockWorkerRequest(
    val imageUrl: String,
)

data class MockWorkerProcessResponse(
    val jobId: String,
    val status: String,
)

data class MockWorkerStatusResponse(
    val jobId: String,
    val status: String,
    val result: String?,
)
