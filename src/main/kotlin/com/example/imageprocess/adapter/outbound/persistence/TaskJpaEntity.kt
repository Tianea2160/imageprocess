package com.example.imageprocess.adapter.outbound.persistence

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tasks")
@DynamicInsert
@DynamicUpdate
class TaskJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val imageUrl: String = "",
    @Column(nullable = false, unique = true, length = 64)
    val fingerprint: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TaskStatus = TaskStatus.PENDING,
    var jobId: String? = null,
    var result: String? = null,
    var failReason: String? = null,
    @Column(nullable = false)
    var retryCount: Int = 0,
    @Column(nullable = false)
    var pollCount: Int = 0,
    var nextPollAt: Instant? = null,
    @Version
    var version: Long = 0,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun toDomain(): Task =
        Task(
            id = id,
            imageUrl = imageUrl,
            fingerprint = fingerprint,
            status = status,
            jobId = jobId,
            result = result,
            failReason = failReason,
            retryCount = retryCount,
            pollCount = pollCount,
            nextPollAt = nextPollAt,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun fromDomain(task: Task): TaskJpaEntity =
            TaskJpaEntity(
                id = task.id,
                imageUrl = task.imageUrl,
                fingerprint = task.fingerprint,
                status = task.status,
                jobId = task.jobId,
                result = task.result,
                failReason = task.failReason,
                retryCount = task.retryCount,
                pollCount = task.pollCount,
                nextPollAt = task.nextPollAt,
                version = task.version,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
            )
    }
}
