package com.example.imageprocess.domain.statemachine.core

fun interface Guard<C> {
    fun evaluate(context: C): Boolean

    companion object {
        fun <C> always(): Guard<C> = Guard { true }

        fun <C> never(): Guard<C> = Guard { false }
    }
}

infix fun <C> Guard<C>.and(other: Guard<C>): Guard<C> = Guard { context -> this.evaluate(context) && other.evaluate(context) }

infix fun <C> Guard<C>.or(other: Guard<C>): Guard<C> = Guard { context -> this.evaluate(context) || other.evaluate(context) }

operator fun <C> Guard<C>.not(): Guard<C> = Guard { context -> !this.evaluate(context) }
