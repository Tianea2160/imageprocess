package com.example.imageprocess.adapter.outbound.persistence

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.Version
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.domain.Persistable
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "tasks")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener::class)
class TaskJpaEntity(
    @Id
    @Column(length = 13)
    private val id: String = "",
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
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
) : Persistable<String> {

    @Transient
    private var _isNew: Boolean = false

    override fun getId(): String = id

    override fun isNew(): Boolean = _isNew

    fun toDomain(): Task =
        Task(
            id = id,
            imageUrl = imageUrl,
            fingerprint = fingerprint,
            state = status,
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
        fun fromDomain(
            task: Task,
            isNew: Boolean = false,
        ): TaskJpaEntity =
            TaskJpaEntity(
                id = task.id,
                imageUrl = task.imageUrl,
                fingerprint = task.fingerprint,
                status = task.state,
                jobId = task.jobId,
                result = task.result,
                failReason = task.failReason,
                retryCount = task.retryCount,
                pollCount = task.pollCount,
                nextPollAt = task.nextPollAt,
                version = task.version,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
            ).also { it._isNew = isNew }
    }
}
