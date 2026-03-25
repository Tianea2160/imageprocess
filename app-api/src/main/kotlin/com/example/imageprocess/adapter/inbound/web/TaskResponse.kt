package com.example.imageprocess.adapter.inbound.web

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "작업 상세 응답")
data class TaskResponse(
    @Schema(description = "작업 ID", example = "01226N0640J7Q", requiredMode = Schema.RequiredMode.REQUIRED)
    val taskId: String,
    @Schema(description = "Job ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val jobId: String?,
    @Schema(description = "이미지 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    val imageUrl: String,
    @Schema(description = "작업 상태", requiredMode = Schema.RequiredMode.REQUIRED)
    val status: TaskStatus,
    @Schema(description = "처리 결과", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val result: String?,
    @Schema(description = "실패 사유", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val failReason: String?,
    @Schema(description = "재시도 횟수", requiredMode = Schema.RequiredMode.REQUIRED)
    val retryCount: Int,
    @Schema(description = "생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    val createdAt: Instant,
    @Schema(description = "수정 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    val updatedAt: Instant,
) {
    companion object {
        fun from(task: Task): TaskResponse =
            TaskResponse(
                taskId = task.id,
                jobId = task.jobId,
                imageUrl = task.imageUrl,
                status = task.state,
                result = task.result,
                failReason = task.failReason,
                retryCount = task.retryCount,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
            )
    }
}

@Schema(description = "작업 생성 응답")
data class CreateTaskResponse(
    @Schema(description = "작업 ID", example = "01226N0640J7Q", requiredMode = Schema.RequiredMode.REQUIRED)
    val taskId: String,
    @Schema(description = "작업 상태", requiredMode = Schema.RequiredMode.REQUIRED)
    val status: TaskStatus,
    @Schema(description = "생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    val createdAt: Instant,
) {
    companion object {
        fun from(task: Task): CreateTaskResponse =
            CreateTaskResponse(
                taskId = task.id,
                status = task.state,
                createdAt = task.createdAt,
            )
    }
}
