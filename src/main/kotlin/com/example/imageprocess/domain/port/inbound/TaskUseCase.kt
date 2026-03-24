package com.example.imageprocess.domain.port.inbound

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus

interface TaskUseCase {
    fun createTask(imageUrl: String): Task

    fun getTask(taskId: String): Task

    fun listTasks(
        page: Int,
        size: Int,
        status: TaskStatus?,
    ): List<Task>

    fun countTasks(status: TaskStatus?): Long
}
