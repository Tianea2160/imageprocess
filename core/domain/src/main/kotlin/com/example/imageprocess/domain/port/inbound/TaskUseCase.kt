package com.example.imageprocess.domain.port.inbound

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface TaskUseCase {
    fun createTask(imageUrl: String): Task

    fun getTask(taskId: String): Task

    fun listTasks(
        pageable: Pageable,
        status: TaskStatus?,
    ): Page<Task>
}
