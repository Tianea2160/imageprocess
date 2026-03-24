package com.example.imageprocess.domain.statemachine.core

import com.example.imageprocess.domain.statemachine.exception.InvalidTransitionException
import kotlin.reflect.KClass

data class TransitionResult<S : State, C : Stateful<S, C>>(
    val previousState: S,
    val newState: S,
    val context: C,
) {
    val stateChanged: Boolean = previousState != newState
}

interface StateMachine<S : State, E : Event, C : Stateful<S, C>> {
    fun fire(
        model: C,
        event: E,
    ): TransitionResult<S, C>

    fun canFire(
        model: C,
        event: E,
    ): Boolean

    fun availableEvents(model: C): Set<KClass<out E>>
}

class DefaultStateMachine<S : State, E : Event, C : Stateful<S, C>>(
    private val transitions: List<Transition<S, E, C>>,
    private val onTransition: ((from: S, event: E, to: S) -> Unit)? = null,
) : StateMachine<S, E, C> {
    override fun fire(
        model: C,
        event: E,
    ): TransitionResult<S, C> {
        val currentState = model.state
        val transition =
            findTransition(currentState, event, model)
                ?: throw InvalidTransitionException(currentState, event)

        val afterActions = transition.executeActions(model, event)
        val newContext = afterActions.withState(transition.target)

        onTransition?.invoke(currentState, event, transition.target)

        return TransitionResult(
            previousState = currentState,
            newState = transition.target,
            context = newContext,
        )
    }

    override fun canFire(
        model: C,
        event: E,
    ): Boolean = findTransition(model.state, event, model) != null

    override fun availableEvents(model: C): Set<KClass<out E>> =
        transitions
            .filter { it.source == model.state && it.guards.all { guard -> guard.evaluate(model) } }
            .map { it.eventType }
            .toSet()

    private fun findTransition(
        currentState: S,
        event: E,
        context: C,
    ): Transition<S, E, C>? = transitions.find { it.isApplicable(currentState, event, context) }
}
