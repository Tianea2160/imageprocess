package com.example.imageprocess

import com.example.imageprocess.TaskIntegrationTest.MockImageProcessorConfig
import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.SubmitResult
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@Import(TestcontainersConfiguration::class, MockImageProcessorConfig::class)
@AutoConfigureMockMvc
class TaskIntegrationTest {
    @TestConfiguration
    class MockImageProcessorConfig {
        @Bean
        @Primary
        fun imageProcessor(): ImageProcessor =
            mockk {
                every { submitImage(any()) } returns SubmitResult.Success(jobId = "mock-job-1")
            }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should create and retrieve task`() {
        val createResult =
            mockMvc
                .post("/api/tasks") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"imageUrl":"https://example.com/integration-test.png"}"""
                }.andExpect {
                    status { isAccepted() }
                    jsonPath("$.taskId") { exists() }
                    jsonPath("$.status") { value("PENDING") }
                }.andReturn()

        val taskId =
            tools.jackson.databind
                .ObjectMapper()
                .readTree(createResult.response.contentAsString)
                .get("taskId")
                .asString()

        mockMvc
            .get("/api/tasks/$taskId")
            .andExpect {
                status { isOk() }
                jsonPath("$.taskId") { value(taskId) }
                jsonPath("$.imageUrl") { value("https://example.com/integration-test.png") }
            }
    }

    @Test
    fun `should return same task for duplicate URL`() {
        val url = "https://example.com/duplicate-test-${System.nanoTime()}.png"

        val first =
            mockMvc
                .post("/api/tasks") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"imageUrl":"$url"}"""
                }.andExpect {
                    status { isAccepted() }
                }.andReturn()

        val second =
            mockMvc
                .post("/api/tasks") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"imageUrl":"$url"}"""
                }.andExpect {
                    status { isAccepted() }
                }.andReturn()

        val mapper = tools.jackson.databind.ObjectMapper()
        val firstId = mapper.readTree(first.response.contentAsString).get("taskId").asString()
        val secondId = mapper.readTree(second.response.contentAsString).get("taskId").asString()

        firstId shouldBe secondId
    }

    @Test
    fun `should return 400 for blank URL`() {
        mockMvc
            .post("/api/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"imageUrl":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_TASK_URL") }
            }
    }

    @Test
    fun `should return 404 for nonexistent task`() {
        mockMvc
            .get("/api/tasks/nonexistent-id")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("TASK_NOT_FOUND") }
            }
    }

    @Test
    fun `should list tasks with pagination`() {
        val baseUrl = "https://example.com/list-test-${System.nanoTime()}"
        repeat(3) { i ->
            mockMvc.post("/api/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"imageUrl":"$baseUrl-$i.png"}"""
            }
        }

        mockMvc
            .get("/api/tasks") {
                param("page", "0")
                param("size", "2")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.totalElements") { isNumber() }
            }
    }
}
