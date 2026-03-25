package com.example.imageprocess.adapter.outbound.redis

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer

@TestConfiguration(proxyBeanMethods = false)
class RedisTestConfig {
    companion object {
        val redisContainer: GenericContainer<*> =
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)
                .apply { start() }
    }

    @Bean
    fun lettuceConnectionFactory(): LettuceConnectionFactory {
        val config = RedisStandaloneConfiguration(redisContainer.host, redisContainer.getMappedPort(6379))
        return LettuceConnectionFactory(config)
    }

    @Bean
    fun stringRedisTemplate(connectionFactory: LettuceConnectionFactory): StringRedisTemplate = StringRedisTemplate(connectionFactory)
}
