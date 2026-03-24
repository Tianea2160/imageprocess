package com.example.imageprocess.adapter.inbound.web

import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.inbound.TaskUseCase
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
class TaskController(
    private val taskUseCase: TaskUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createTask(
        @RequestBody request: CreateTaskRequest,
    ): CreateTaskResponse {
        val task = taskUseCase.createTask(request.imageUrl)
        return CreateTaskResponse.from(task)
    }

    @GetMapping("/{taskId}")
    fun getTask(
        @PathVariable taskId: UUID,
    ): TaskResponse {
        val task = taskUseCase.getTask(taskId)
        return TaskResponse.from(task)
    }

    @GetMapping
    fun listTasks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
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
