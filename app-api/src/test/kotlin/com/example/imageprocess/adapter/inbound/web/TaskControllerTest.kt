package com.example.imageprocess.adapter.inbound.web

import com.example.imageprocess.domain.exception.InvalidTaskUrlException
import com.example.imageprocess.domain.exception.TaskNotFoundException
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.inbound.TaskUseCase
import com.example.imageprocess.fixture.TaskFixture
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(TaskController::class)
@Import(TaskControllerTest.MockConfig::class)
class TaskControllerTest {
    class MockConfig {
        @Bean
        fun taskUseCase(): TaskUseCase = mockk()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var taskUseCase: TaskUseCase

    @BeforeEach
    fun setUp() {
        clearMocks(taskUseCase)
    }

    @Test
    fun `POST should create task and return 202`() {
        val task = TaskFixture.create()
        every { taskUseCase.createTask("https://example.com/image.png") } returns task

        mockMvc
            .post("/api/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"imageUrl":"https://example.com/image.png"}"""
            }.andExpect {
                status { isAccepted() }
                jsonPath("$.taskId") { value("01H5N0640J7Q") }
                jsonPath("$.status") { value("PENDING") }
            }
    }

    @Test
    fun `POST should return 400 for blank URL`() {
        every { taskUseCase.createTask("") } throws InvalidTaskUrlException("imageUrl must not be blank")

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
    fun `POST should return 400 for non-HTTP URL`() {
        every { taskUseCase.createTask("ftp://example.com/file") } throws
            InvalidTaskUrlException("imageUrl must be an HTTP or HTTPS URL")

        mockMvc
            .post("/api/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"imageUrl":"ftp://example.com/file"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `GET by id should return task`() {
        val task = TaskFixture.create()
        every { taskUseCase.getTask("01H5N0640J7Q") } returns task

        mockMvc
            .get("/api/tasks/01H5N0640J7Q")
            .andExpect {
                status { isOk() }
                jsonPath("$.taskId") { value("01H5N0640J7Q") }
                jsonPath("$.imageUrl") { value("https://example.com/image.png") }
                jsonPath("$.status") { value("PENDING") }
            }
    }

    @Test
    fun `GET by id should return 404 when not found`() {
        every { taskUseCase.getTask("nonexistent") } throws TaskNotFoundException("nonexistent")

        mockMvc
            .get("/api/tasks/nonexistent")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("TASK_NOT_FOUND") }
            }
    }

    @Test
    fun `GET list should return paginated tasks`() {
        val tasks = listOf(TaskFixture.create(id = "task-1"), TaskFixture.create(id = "task-2"))
        val page = PageImpl(tasks, PageRequest.of(0, 20), 2)
        every { taskUseCase.listTasks(any(), isNull()) } returns page

        mockMvc
            .get("/api/tasks")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.content[0].taskId") { value("task-1") }
            }
    }

    @Test
    fun `GET list should filter by status`() {
        val tasks = listOf(TaskFixture.create(id = "task-1", state = TaskStatus.COMPLETED))
        val page = PageImpl(tasks, PageRequest.of(0, 20), 1)
        every { taskUseCase.listTasks(any(), eq(TaskStatus.COMPLETED)) } returns page

        mockMvc
            .get("/api/tasks") {
                param("status", "COMPLETED")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
            }
    }
}
