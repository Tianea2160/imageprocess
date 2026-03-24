package com.example.imageprocess.domain.statemachine.dsl

import com.example.imageprocess.domain.statemachine.core.Action
import com.example.imageprocess.domain.statemachine.core.DefaultStateMachine
import com.example.imageprocess.domain.statemachine.core.Event
import com.example.imageprocess.domain.statemachine.core.Guard
import com.example.imageprocess.domain.statemachine.core.State
import com.example.imageprocess.domain.statemachine.core.StateMachine
import com.example.imageprocess.domain.statemachine.core.Stateful
import com.example.imageprocess.domain.statemachine.core.Transition
import kotlin.reflect.KClass
import kotlin.reflect.cast

@DslMarker
annotation class StateMachineDsl

@StateMachineDsl
class StateMachineBuilder<S : State, E : Event, C : Stateful<S, C>> {
    private val transitions = mutableListOf<Transition<S, E, C>>()
    private var onTransition: ((S, E, S) -> Unit)? = null

    fun from(
        state: S,
        block: StateBuilder<S, E, C>.() -> Unit,
    ) {
        val stateBuilder = StateBuilder<S, E, C>(state)
        stateBuilder.block()
        transitions.addAll(stateBuilder.build())
    }

    fun onTransition(callback: (from: S, event: E, to: S) -> Unit) {
        onTransition = callback
    }

    fun build(): StateMachine<S, E, C> =
        DefaultStateMachine(
            transitions = transitions.toList(),
            onTransition = onTransition,
        )
}

@StateMachineDsl
class StateBuilder<S : State, E : Event, C : Stateful<S, C>>(
    @PublishedApi internal val source: S,
) {
    @PublishedApi
    internal val transitionBuilders = mutableListOf<TransitionConfig<S, E, C, out E>>()

    inline fun <reified T : E> on(): TransitionStart<S, E, C, T> = TransitionStart(source, T::class) { transitionBuilders.add(it) }

    fun build(): List<Transition<S, E, C>> = transitionBuilders.map { it.build() }
}

@StateMachineDsl
class TransitionStart<S : State, E : Event, C : Stateful<S, C>, T : E>(
    private val source: S,
    private val eventClass: KClass<T>,
    private val onComplete: (TransitionConfig<S, E, C, T>) -> Unit,
) {
    infix fun goto(target: S): TransitionConfig<S, E, C, T> {
        val config = TransitionConfig<S, E, C, T>(source, eventClass, target)
        onComplete(config)
        return config
    }
}

@StateMachineDsl
class TransitionConfig<S : State, E : Event, C : Stateful<S, C>, T : E>(
    private val source: S,
    private val eventClass: KClass<T>,
    private val target: S,
) {
    private val guards = mutableListOf<Guard<C>>()
    private val actions = mutableListOf<Action<C, E>>()

    infix fun guardedBy(predicate: (C) -> Boolean): TransitionConfig<S, E, C, T> {
        guards.add(Guard(predicate))
        return this
    }

    infix fun guards(block: GuardsBuilder<C>.() -> Unit): TransitionConfig<S, E, C, T> {
        val builder = GuardsBuilder<C>()
        builder.block()
        guards.addAll(builder.build())
        return this
    }

    infix fun action(block: (C, T) -> C): TransitionConfig<S, E, C, T> {
        actions.add(Action { context, event -> block(context, eventClass.cast(event)) })
        return this
    }

    infix fun actions(block: ActionsBuilder<C, E, T>.() -> Unit): TransitionConfig<S, E, C, T> {
        val builder = ActionsBuilder<C, E, T>(eventClass)
        builder.block()
        actions.addAll(builder.build())
        return this
    }

    fun build(): Transition<S, E, C> =
        Transition(
            source = source,
            eventType = eventClass,
            target = target,
            guards = guards.toList(),
            actions = actions.toList(),
        )
}

@StateMachineDsl
class GuardsBuilder<C> {
    private val guards = mutableListOf<Guard<C>>()

    fun guard(predicate: (C) -> Boolean) {
        guards.add(Guard(predicate))
    }

    operator fun Guard<C>.unaryPlus() {
        guards.add(this)
    }

    fun build(): List<Guard<C>> = guards.toList()
}

@StateMachineDsl
class ActionsBuilder<C, E : Event, T : E>(
    private val eventClass: KClass<T>,
) {
    private val actions = mutableListOf<Action<C, E>>()

    fun action(block: (C, T) -> C) {
        actions.add(Action { context, event -> block(context, eventClass.cast(event)) })
    }

    operator fun Action<C, E>.unaryPlus() {
        actions.add(this)
    }

    fun build(): List<Action<C, E>> = actions.toList()
}

inline fun <reified S : State, reified E : Event, C : Stateful<S, C>> stateMachine(
    block: StateMachineBuilder<S, E, C>.() -> Unit,
): StateMachine<S, E, C> {
    val builder = StateMachineBuilder<S, E, C>()
    builder.block()
    return builder.build()
}
