package com.example.imageprocess.domain.port.outbound

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant

interface TaskRepository {
    fun save(task: Task): Task

    fun findById(id: String): Task?

    fun findByFingerprint(fingerprint: String): Task?

    fun findPollableTasks(
        now: Instant,
        limit: Int,
    ): List<Task>

    fun findByStatusIn(statuses: List<TaskStatus>): List<Task>

    fun findAll(
        pageable: Pageable,
        status: TaskStatus?,
    ): Page<Task>
}
