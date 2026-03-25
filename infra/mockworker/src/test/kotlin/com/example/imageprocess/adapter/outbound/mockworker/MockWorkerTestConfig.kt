package com.example.imageprocess.adapter.outbound.mockworker

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.resilience.annotation.EnableResilientMethods
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@TestConfiguration
@EnableResilientMethods
class MockWorkerTestConfig {
    @Bean
    fun objectMapper(): ObjectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
}
