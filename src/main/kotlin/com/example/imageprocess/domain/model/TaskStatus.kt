package com.example.imageprocess.domain.model

import com.example.imageprocess.domain.statemachine.core.State

enum class TaskStatus : State {
    PENDING,
    SUBMITTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRY_WAITING,
    ;

    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED

    fun isPollable(): Boolean = this == SUBMITTED || this == PROCESSING
}
