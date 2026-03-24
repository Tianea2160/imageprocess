package com.example.imageprocess.adapter.inbound.web

import com.example.imageprocess.domain.model.TaskStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@Tag(name = "Task", description = "이미지 처리 작업 관리 API")
interface TaskApi {
    @Operation(
        summary = "이미지 처리 작업 생성",
        description = "이미지 URL을 받아 비동기 처리 작업을 생성합니다. 동일 URL은 멱등 처리됩니다.",
        responses = [ApiResponse(responseCode = "202", description = "작업 생성 완료")],
    )
    fun createTask(request: CreateTaskRequest): CreateTaskResponse

    @Operation(
        summary = "작업 상태 조회",
        description = "작업 ID로 현재 처리 상태를 조회합니다.",
        responses = [ApiResponse(responseCode = "200", description = "조회 성공")],
    )
    fun getTask(
        @Parameter(description = "작업 ID", required = true, example = "01226N0640J7Q")
        taskId: String,
    ): TaskResponse

    @Operation(
        summary = "작업 목록 조회",
        description = "페이지네이션과 상태 필터를 지원하는 작업 목록을 조회합니다.",
        responses = [ApiResponse(responseCode = "200", description = "조회 성공")],
    )
    fun listTasks(
        @ParameterObject pageable: Pageable,
        @Parameter(description = "상태 필터")
        status: TaskStatus?,
    ): Page<TaskResponse>
}
