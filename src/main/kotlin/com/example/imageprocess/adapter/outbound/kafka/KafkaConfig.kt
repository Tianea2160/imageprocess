package com.example.imageprocess.adapter.outbound.kafka

import com.example.imageprocess.adapter.kafka.KafkaTopics
import com.example.imageprocess.adapter.kafka.TaskSubmitMessage
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JacksonJsonSerializer

@Configuration
class KafkaConfig(
    private val kafkaProperties: KafkaProperties,
) {
    @Bean
    fun taskSubmitTopic(): NewTopic =
        TopicBuilder
            .name(KafkaTopics.TASK_SUBMIT)
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun taskSubmitKafkaTemplate(): KafkaTemplate<String, TaskSubmitMessage> {
        val producerProps = kafkaProperties.buildProducerProperties()
        producerProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        producerProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonJsonSerializer::class.java
        return KafkaTemplate(DefaultKafkaProducerFactory(producerProps))
    }
}
