package com.example.imageprocess.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TaskStatusTest {
    @ParameterizedTest
    @CsvSource(
        "PENDING, SUBMITTED",
        "PENDING, FAILED",
        "SUBMITTED, PROCESSING",
        "SUBMITTED, FAILED",
        "SUBMITTED, RETRY_WAITING",
        "PROCESSING, COMPLETED",
        "PROCESSING, FAILED",
        "PROCESSING, RETRY_WAITING",
        "RETRY_WAITING, SUBMITTED",
        "RETRY_WAITING, FAILED",
    )
    fun `should allow valid transitions`(
        from: TaskStatus,
        to: TaskStatus,
    ) {
        assertTrue(TaskStatus.canTransition(from, to))
    }

    @ParameterizedTest
    @CsvSource(
        "COMPLETED, PENDING",
        "COMPLETED, SUBMITTED",
        "COMPLETED, PROCESSING",
        "COMPLETED, FAILED",
        "COMPLETED, RETRY_WAITING",
        "FAILED, PENDING",
        "FAILED, SUBMITTED",
        "FAILED, PROCESSING",
        "FAILED, COMPLETED",
        "FAILED, RETRY_WAITING",
        "PROCESSING, PENDING",
        "SUBMITTED, PENDING",
        "PENDING, PROCESSING",
        "PENDING, COMPLETED",
        "PENDING, RETRY_WAITING",
    )
    fun `should reject invalid transitions`(
        from: TaskStatus,
        to: TaskStatus,
    ) {
        assertFalse(TaskStatus.canTransition(from, to))
    }

    @Test
    fun `terminal states should be COMPLETED and FAILED`() {
        assertTrue(TaskStatus.COMPLETED.isTerminal())
        assertTrue(TaskStatus.FAILED.isTerminal())
        assertFalse(TaskStatus.PENDING.isTerminal())
        assertFalse(TaskStatus.SUBMITTED.isTerminal())
        assertFalse(TaskStatus.PROCESSING.isTerminal())
        assertFalse(TaskStatus.RETRY_WAITING.isTerminal())
    }

    @Test
    fun `pollable states should be SUBMITTED and PROCESSING`() {
        assertTrue(TaskStatus.SUBMITTED.isPollable())
        assertTrue(TaskStatus.PROCESSING.isPollable())
        assertFalse(TaskStatus.PENDING.isPollable())
        assertFalse(TaskStatus.COMPLETED.isPollable())
        assertFalse(TaskStatus.FAILED.isPollable())
        assertFalse(TaskStatus.RETRY_WAITING.isPollable())
    }
}
