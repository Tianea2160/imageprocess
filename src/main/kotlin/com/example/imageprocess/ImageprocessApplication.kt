package com.example.imageprocess

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableResilientMethods
class ImageprocessApplication

fun main(args: Array<String>) {
    runApplication<ImageprocessApplication>(*args)
}
