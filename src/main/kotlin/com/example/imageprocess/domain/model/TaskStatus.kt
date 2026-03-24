package com.example.imageprocess.domain.model

enum class TaskStatus {
    PENDING,
    SUBMITTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRY_WAITING,
    ;

    companion object {
        private val allowedTransitions =
            mapOf(
                PENDING to setOf(SUBMITTED, FAILED),
                SUBMITTED to setOf(PROCESSING, FAILED, RETRY_WAITING),
                PROCESSING to setOf(COMPLETED, FAILED, RETRY_WAITING),
                RETRY_WAITING to setOf(SUBMITTED, FAILED),
            )

        fun canTransition(
            from: TaskStatus,
            to: TaskStatus,
        ): Boolean = allowedTransitions[from]?.contains(to) ?: false
    }

    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED

    fun isPollable(): Boolean = this == SUBMITTED || this == PROCESSING
}
