package com.example.imageprocess.domain.statemachine.core

interface Stateful<S : State, Self : Stateful<S, Self>> {
    val state: S

    fun withState(newState: S): Self
}
