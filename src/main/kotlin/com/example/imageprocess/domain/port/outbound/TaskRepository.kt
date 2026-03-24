package com.example.imageprocess.domain.port.outbound

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import java.time.Instant
import java.util.UUID

interface TaskRepository {
    fun save(task: Task): Task

    fun findById(id: UUID): Task?

    fun findByFingerprint(fingerprint: String): Task?

    fun findPollableTasks(
        now: Instant,
        limit: Int,
    ): List<Task>

    fun findByStatusIn(statuses: List<TaskStatus>): List<Task>

    fun findAll(
        page: Int,
        size: Int,
        status: TaskStatus?,
    ): List<Task>

    fun count(status: TaskStatus?): Long
}
