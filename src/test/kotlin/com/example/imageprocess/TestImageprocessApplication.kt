package com.example.imageprocess

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<ImageprocessApplication>().with(TestcontainersConfiguration::class).run(*args)
}
