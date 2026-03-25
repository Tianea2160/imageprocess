package com.example.imageprocess

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<ImageprocessApiApplication>().with(TestcontainersConfiguration::class).run(*args)
}
