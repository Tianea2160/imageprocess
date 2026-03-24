package com.example.imageprocess.adapter.inbound.web

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import java.time.Instant
import java.util.UUID

data class TaskResponse(
    val taskId: UUID,
    val imageUrl: String,
    val status: TaskStatus,
    val result: String?,
    val failReason: String?,
    val retryCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(task: Task): TaskResponse =
            TaskResponse(
                taskId = task.id,
                imageUrl = task.imageUrl,
                status = task.status,
                result = task.result,
                failReason = task.failReason,
                retryCount = task.retryCount,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
            )
    }
}

data class TaskListResponse(
    val tasks: List<TaskResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

data class CreateTaskResponse(
    val taskId: UUID,
    val status: TaskStatus,
    val createdAt: Instant,
) {
    companion object {
        fun from(task: Task): CreateTaskResponse =
            CreateTaskResponse(
                taskId = task.id,
                status = task.status,
                createdAt = task.createdAt,
            )
    }
}
