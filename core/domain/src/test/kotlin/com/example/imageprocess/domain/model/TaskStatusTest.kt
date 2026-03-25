package com.example.imageprocess.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TaskStatusTest {
    @Test
    fun `terminal states should be COMPLETED and FAILED`() {
        TaskStatus.COMPLETED.isTerminal() shouldBe true
        TaskStatus.FAILED.isTerminal() shouldBe true
        TaskStatus.PENDING.isTerminal() shouldBe false
        TaskStatus.SUBMITTED.isTerminal() shouldBe false
        TaskStatus.PROCESSING.isTerminal() shouldBe false
        TaskStatus.RETRY_WAITING.isTerminal() shouldBe false
    }

    @Test
    fun `pollable states should be SUBMITTED and PROCESSING`() {
        TaskStatus.SUBMITTED.isPollable() shouldBe true
        TaskStatus.PROCESSING.isPollable() shouldBe true
        TaskStatus.PENDING.isPollable() shouldBe false
        TaskStatus.COMPLETED.isPollable() shouldBe false
        TaskStatus.FAILED.isPollable() shouldBe false
        TaskStatus.RETRY_WAITING.isPollable() shouldBe false
    }
}
