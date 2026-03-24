package com.example.imageprocess.domain.exception

import com.example.imageprocess.domain.model.TaskStatus

class TaskNotFoundException(
    taskId: String,
) : NotFoundException("Task not found: $taskId", "TASK_NOT_FOUND")

class InvalidTaskStateTransitionException(
    from: TaskStatus,
    to: TaskStatus,
) : ConflictException("Invalid state transition: $from -> $to", "INVALID_TASK_STATE_TRANSITION")

class InvalidTaskUrlException(
    reason: String,
) : BadRequestException(reason, "INVALID_TASK_URL")
