package com.example.imageprocess.domain.exception

class TaskNotFoundException(
    taskId: String,
) : NotFoundException("Task not found: $taskId", "TASK_NOT_FOUND")

class InvalidTaskUrlException(
    reason: String,
) : BadRequestException(reason, "INVALID_TASK_URL")
