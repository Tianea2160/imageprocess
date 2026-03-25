package com.example.imageprocess.domain.statemachine.exception

import com.example.imageprocess.domain.statemachine.core.Event
import com.example.imageprocess.domain.statemachine.core.State

sealed class StateMachineException(
    message: String,
) : RuntimeException(message)

class InvalidTransitionException(
    val currentState: State,
    val event: Event,
) : StateMachineException(
        "No valid transition from state '$currentState' with event '${event::class.simpleName}'",
    )
