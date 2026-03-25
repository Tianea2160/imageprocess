package com.example.imageprocess.adapter.outbound.mockworker

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

@Component
class MockWorkerClient(
    @Value("\${mock-worker.base-url}") private val baseUrl: String,
    @Value("\${mock-worker.api-key}") private val apiKey: String,
    @Value("\${mock-worker.connect-timeout}") private val connectTimeout: Duration,
    @Value("\${mock-worker.read-timeout}") private val readTimeout: Duration,
    private val objectMapper: ObjectMapper,
) {
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

    @Retryable(
        includes = [RetryableWorkerException::class],
        maxRetriesString = "\${mock-worker.max-retries:2}",
        delay = 100,
        multiplier = 2.0,
    )
    fun submitImage(imageUrl: String): MockWorkerProcessResponse =
        try {
            restClient
                .post()
                .uri("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .body(MockWorkerRequest(imageUrl))
                .retrieve()
                .body<MockWorkerProcessResponse>()
                ?: throw RetryableWorkerException("Empty response from Mock Worker")
        } catch (e: RestClientResponseException) {
            handleResponseError("submitImage", e)
        }

    @Retryable(
        includes = [RetryableWorkerException::class],
        maxRetriesString = "\${mock-worker.max-retries:2}",
        delay = 100,
        multiplier = 2.0,
    )
    fun getJobStatus(jobId: String): MockWorkerStatusResponse =
        try {
            restClient
                .get()
                .uri("/process/{jobId}", jobId)
                .retrieve()
                .body<MockWorkerStatusResponse>()
                ?: throw RetryableWorkerException("Empty response from Mock Worker")
        } catch (e: RestClientResponseException) {
            handleResponseError("getJobStatus", e)
        }

    private fun handleResponseError(
        method: String,
        e: RestClientResponseException,
    ): Nothing {
        val detail = parseErrorDetail(e.responseBodyAsString)
        val statusCode = e.statusCode.value()
        if (statusCode in nonRetryableStatusCodes) {
            log.warn("{} rejected with non-retryable status {}: {}", method, statusCode, detail)
            throw NonRetryableWorkerException(detail, e)
        }
        if (statusCode == 429) {
            val retryAfter = e.responseHeaders?.getFirst("retry-after")?.toLongOrNull()
            log.warn("{} rate limited (429), retry-after: {}s, detail: {}", method, retryAfter, detail)
            throw RateLimitedWorkerException("Mock Worker returned 429: $detail", retryAfter, e)
        }
        log.warn("{} failed with status {}: {}", method, statusCode, detail)
        throw RetryableWorkerException("Mock Worker returned $statusCode: $detail", e)
    }

    private fun parseErrorDetail(body: String): String =
        try {
            val node = objectMapper.readTree(body)
            node.get("detail")?.asString() ?: body
        } catch (_: Exception) {
            body
        }
}
