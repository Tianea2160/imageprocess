package com.example.imageprocess.domain.statemachine.core

import kotlin.reflect.KClass

data class Transition<S : State, E : Event, C : Stateful<S, C>>(
    val source: S,
    val eventType: KClass<out E>,
    val target: S,
    val guards: List<Guard<C>> = emptyList(),
    val actions: List<Action<C, E>> = emptyList(),
) {
    fun isApplicable(
        currentState: S,
        triggeredEvent: E,
        context: C,
    ): Boolean =
        currentState == source &&
            eventType.isInstance(triggeredEvent) &&
            guards.all { it.evaluate(context) }

    fun executeActions(
        context: C,
        event: E,
    ): C = actions.fold(context) { acc, action -> action.execute(acc, event) }
}
