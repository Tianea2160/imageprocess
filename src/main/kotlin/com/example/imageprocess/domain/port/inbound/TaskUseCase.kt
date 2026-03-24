package com.example.imageprocess.domain.port.inbound

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import java.util.UUID

interface TaskUseCase {
    fun createTask(imageUrl: String): Task

    fun getTask(taskId: UUID): Task

    fun listTasks(
        page: Int,
        size: Int,
        status: TaskStatus?,
    ): List<Task>

    fun countTasks(status: TaskStatus?): Long
}
