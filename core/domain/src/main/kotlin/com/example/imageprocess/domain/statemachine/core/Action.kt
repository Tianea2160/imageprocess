package com.example.imageprocess.domain.statemachine.core

fun interface Action<C, E : Event> {
    fun execute(
        context: C,
        event: E,
    ): C

    companion object {
        fun <C, E : Event> noOp(): Action<C, E> = Action { context, _ -> context }
    }
}

infix fun <C, E : Event> Action<C, E>.then(next: Action<C, E>): Action<C, E> =
    Action { context, event -> next.execute(this.execute(context, event), event) }
