package com.example.imageprocess.adapter.outbound.kafka

import com.example.imageprocess.adapter.kafka.KafkaTopics
import com.example.imageprocess.adapter.kafka.TaskSubmitMessage
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import tools.jackson.databind.json.JsonMapper

@Configuration
class KafkaConfig(
    private val producerFactory: ProducerFactory<String, Any>,
    private val consumerFactory: ConsumerFactory<String, Any>,
    private val jsonMapper: JsonMapper,
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
        val configs = producerFactory.configurationProperties.toMutableMap()
        val factory =
            DefaultKafkaProducerFactory<String, TaskSubmitMessage>(
                configs,
                StringSerializer(),
                JacksonJsonSerializer(jsonMapper),
            )
        return KafkaTemplate(factory)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val configs = consumerFactory.configurationProperties.toMutableMap()

        val deserializer =
            JacksonJsonDeserializer<Any>(jsonMapper)
                .trustedPackages("com.example.imageprocess.adapter.kafka")

        val factory = DefaultKafkaConsumerFactory(configs, StringDeserializer(), deserializer)

        return ConcurrentKafkaListenerContainerFactory<String, Any>().also {
            it.setConsumerFactory(factory)
        }
    }
}
