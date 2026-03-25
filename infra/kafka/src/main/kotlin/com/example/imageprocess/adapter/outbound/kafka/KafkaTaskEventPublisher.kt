package com.example.imageprocess.adapter.outbound.kafka

import com.example.imageprocess.adapter.kafka.KafkaTopics
import com.example.imageprocess.adapter.kafka.TaskSubmitMessage
import com.example.imageprocess.domain.port.outbound.TaskEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaTaskEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, TaskSubmitMessage>,
) : TaskEventPublisher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun publishSubmitTask(
        taskId: String,
        imageUrl: String,
    ) {
        val message = TaskSubmitMessage(taskId = taskId, imageUrl = imageUrl)
        kafkaTemplate.send(KafkaTopics.TASK_SUBMIT, taskId, message)
        log.info("Published submit event for task {}", taskId)
    }
}
