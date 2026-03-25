package com.example.imageprocess.adapter.inbound.web

import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.inbound.TaskUseCase
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val taskUseCase: TaskUseCase,
) : TaskApi {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun createTask(
        @RequestBody request: CreateTaskRequest,
    ): CreateTaskResponse {
        val task = taskUseCase.createTask(request.imageUrl)
        return CreateTaskResponse.from(task)
    }

    @GetMapping("/{taskId}")
    override fun getTask(
        @PathVariable taskId: String,
    ): TaskResponse {
        val task = taskUseCase.getTask(taskId)
        return TaskResponse.from(task)
    }

    @GetMapping
    override fun listTasks(
        @PageableDefault(size = 20) pageable: Pageable,
        @RequestParam(required = false) status: TaskStatus?,
    ): Page<TaskResponse> = taskUseCase.listTasks(pageable, status).map { TaskResponse.from(it) }
}
