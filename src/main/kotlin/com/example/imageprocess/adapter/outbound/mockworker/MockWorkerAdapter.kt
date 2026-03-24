package com.example.imageprocess.adapter.outbound.mockworker

import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.ProcessResult
import com.example.imageprocess.domain.port.outbound.ProcessStatusResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class MockWorkerAdapter(
    @Value("\${mock-worker.base-url}") private val baseUrl: String,
    @Value("\${mock-worker.api-key}") private val apiKey: String,
    @Value("\${mock-worker.connect-timeout}") private val connectTimeout: Duration,
    @Value("\${mock-worker.read-timeout}") private val readTimeout: Duration,
) : ImageProcessor {
    private val restClient: RestClient by lazy {
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .defaultHeader("X-API-KEY", apiKey)
            .build()
    }

    override fun submitImage(imageUrl: String): ProcessResult {
        val response =
            restClient
                .post()
                .uri("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .body(MockWorkerRequest(imageUrl))
                .retrieve()
                .body(MockWorkerProcessResponse::class.java)
                ?: throw RuntimeException("Empty response from Mock Worker")

        return ProcessResult(
            jobId = response.jobId,
            status = response.status,
        )
    }

    override fun getJobStatus(jobId: String): ProcessStatusResult {
        val response =
            restClient
                .get()
                .uri("/process/{jobId}", jobId)
                .retrieve()
                .body(MockWorkerStatusResponse::class.java)
                ?: throw RuntimeException("Empty response from Mock Worker")

        return ProcessStatusResult(
            jobId = response.jobId,
            status = response.status,
            result = response.result,
        )
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
