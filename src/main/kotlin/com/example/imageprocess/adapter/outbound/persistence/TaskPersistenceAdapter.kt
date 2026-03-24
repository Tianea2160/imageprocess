package com.example.imageprocess.adapter.outbound.persistence

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.TaskRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TaskPersistenceAdapter(
    private val jpaRepository: TaskJpaRepository,
) : TaskRepository {
    override fun save(task: Task): Task {
        val entity = TaskJpaEntity.fromDomain(task)
        return jpaRepository.save(entity).toDomain()
    }

    override fun findById(id: String): Task? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByFingerprint(fingerprint: String): Task? = jpaRepository.findByFingerprint(fingerprint)?.toDomain()

    override fun findPollableTasks(
        now: Instant,
        limit: Int,
    ): List<Task> = jpaRepository.findPollableTasks(now, PageRequest.of(0, limit)).map { it.toDomain() }

    override fun findByStatusIn(statuses: List<TaskStatus>): List<Task> = jpaRepository.findByStatusIn(statuses).map { it.toDomain() }

    override fun findAll(
        page: Int,
        size: Int,
        status: TaskStatus?,
    ): List<Task> {
        val pageable = PageRequest.of(page, size)
        return if (status != null) {
            jpaRepository.findByStatus(status, pageable)
        } else {
            jpaRepository.findAll(pageable).content
        }.map { it.toDomain() }
    }

    override fun count(status: TaskStatus?): Long =
        if (status != null) {
            jpaRepository.countByStatus(status)
        } else {
            jpaRepository.count()
        }
}
