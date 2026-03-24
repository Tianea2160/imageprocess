package com.example.imageprocess.domain.model

import com.example.imageprocess.domain.statemachine.core.Event

sealed interface TaskEvent : Event {
    data class Submit(
        val jobId: String,
    ) : TaskEvent

    data object StartProcessing : TaskEvent

    data class Complete(
        val result: String,
    ) : TaskEvent

    data class Fail(
        val reason: String,
    ) : TaskEvent

    data object RetryWait : TaskEvent

    data object RecoverToSubmitted : TaskEvent
}
