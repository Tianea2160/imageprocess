package com.example.imageprocess.adapter.outbound.persistence

import com.example.imageprocess.domain.model.TaskStatus
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import java.time.Instant
import java.util.UUID

interface TaskJpaRepository : JpaRepository<TaskJpaEntity, UUID> {
    fun findByFingerprint(fingerprint: String): TaskJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
        """
        SELECT t FROM TaskJpaEntity t
        WHERE t.status IN ('SUBMITTED', 'PROCESSING')
        AND t.nextPollAt <= :now
        ORDER BY t.nextPollAt ASC
        """,
    )
    fun findPollableTasks(
        now: Instant,
        pageable: Pageable,
    ): List<TaskJpaEntity>

    fun findByStatusIn(statuses: List<TaskStatus>): List<TaskJpaEntity>

    fun findByStatus(
        status: TaskStatus,
        pageable: Pageable,
    ): List<TaskJpaEntity>

    fun countByStatus(status: TaskStatus): Long
}
