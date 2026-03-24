package com.example.imageprocess.adapter.inbound.web

import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.inbound.TaskUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Task", description = "이미지 처리 작업 관리 API")
class TaskController(
    private val taskUseCase: TaskUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "이미지 처리 작업 생성", description = "이미지 URL을 받아 비동기 처리 작업을 생성합니다. 동일 URL은 멱등 처리됩니다.")
    fun createTask(
        @RequestBody request: CreateTaskRequest,
    ): CreateTaskResponse {
        val task = taskUseCase.createTask(request.imageUrl)
        return CreateTaskResponse.from(task)
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "작업 상태 조회", description = "작업 ID로 현재 처리 상태를 조회합니다.")
    fun getTask(
        @Parameter(description = "작업 ID", required = true)
        @PathVariable taskId: UUID,
    ): TaskResponse {
        val task = taskUseCase.getTask(taskId)
        return TaskResponse.from(task)
    }

    @GetMapping
    @Operation(summary = "작업 목록 조회", description = "페이지네이션과 상태 필터를 지원하는 작업 목록을 조회합니다.")
    fun listTasks(
        @Parameter(description = "페이지 번호 (0부터 시작)")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "상태 필터")
        @RequestParam(required = false) status: TaskStatus?,
    ): TaskListResponse {
        val tasks = taskUseCase.listTasks(page, size, status)
        val totalCount = taskUseCase.countTasks(status)
        return TaskListResponse(
            tasks = tasks.map { TaskResponse.from(it) },
            page = page,
            size = size,
            totalCount = totalCount,
        )
    }
}
