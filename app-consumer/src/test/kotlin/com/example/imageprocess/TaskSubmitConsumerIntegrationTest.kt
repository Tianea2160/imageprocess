package com.example.imageprocess

import com.example.imageprocess.adapter.kafka.KafkaTopics
import com.example.imageprocess.adapter.kafka.TaskSubmitMessage
import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.port.outbound.CircuitBreaker
import com.example.imageprocess.domain.port.outbound.ImageProcessor
import com.example.imageprocess.domain.port.outbound.SubmitResult
import com.example.imageprocess.domain.port.outbound.TaskRepository
import com.example.imageprocess.fixture.TaskFixture
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.common.TopicPartition
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(
    properties = [
        "rate-limiter.max-tokens=10",
        "rate-limiter.refill-rate=10",
        "rate-limiter.refill-interval-ms=1000",
        "circuit-breaker.failure-threshold=3",
        "circuit-breaker.cooldown-seconds=2",
        "circuit-breaker.half-open-max-calls=1",
        "circuit-breaker.failure-window-seconds=30",
    ],
)
@Import(TestcontainersConfiguration::class, TaskSubmitConsumerIntegrationTest.MockImageProcessorConfig::class)
class TaskSubmitConsumerIntegrationTest {
    @TestConfiguration
    class MockImageProcessorConfig {
        @Bean
        @Primary
        fun imageProcessor(): ImageProcessor = mockk()
    }

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, TaskSubmitMessage>

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var imageProcessor: ImageProcessor

    @Autowired
    private lateinit var circuitBreaker: CircuitBreaker

    @Autowired
    private lateinit var endpointRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var kafkaAdmin: KafkaAdmin

    @BeforeEach
    fun setUp() {
        circuitBreaker.recordSuccess()
        startAllContainers()
    }

    @AfterEach
    fun tearDown() {
        stopAllContainers()
        purgeRetryTopics()
        circuitBreaker.recordSuccess()
    }

    private fun stopAllContainers() {
        endpointRegistry.listenerContainers.forEach { it.stop() }
    }

    private fun startAllContainers() {
        endpointRegistry.listenerContainers.forEach { if (!it.isRunning) it.start() }
    }

    private fun purgeRetryTopics() {
        val retryTopics =
            listOf(
                "${KafkaTopics.TASK_SUBMIT}-retry-2000",
                "${KafkaTopics.TASK_SUBMIT}-retry-4000",
                "${KafkaTopics.TASK_SUBMIT}-retry-8000",
                "${KafkaTopics.TASK_SUBMIT}-dlt",
            )

        AdminClient.create(kafkaAdmin.configurationProperties).use { admin ->
            val existingTopics = admin.listTopics().names().get()
            val topicsToDelete = retryTopics.filter { it in existingTopics }
            if (topicsToDelete.isEmpty()) return

            val topicDescriptions = admin.describeTopics(topicsToDelete).allTopicNames().get()
            val recordsToDelete = mutableMapOf<TopicPartition, RecordsToDelete>()

            topicDescriptions.forEach { (topic, desc) ->
                desc.partitions().forEach { partition ->
                    recordsToDelete[TopicPartition(topic, partition.partition())] =
                        RecordsToDelete.beforeOffset(-1) // delete all
                }
            }

            if (recordsToDelete.isNotEmpty()) {
                admin.deleteRecords(recordsToDelete).all().get()
            }
        }
    }

    private fun nextId(): String {
        val seq = idCounter.incrementAndGet()
        return "T%012d".format(seq).takeLast(13)
    }

    private fun uniqueImageUrl(): String = "https://example.com/${System.nanoTime()}.png"

    private fun createAndSaveTask(): Task {
        val task = TaskFixture.create(id = nextId(), imageUrl = uniqueImageUrl())
        taskRepository.save(task)
        return task
    }

    @Test
    fun `should submit task and transition to SUBMITTED on success`() {
        val task = createAndSaveTask()

        every { imageProcessor.submitImage(task.imageUrl) } returns
            SubmitResult.Success(jobId = "job-success")

        kafkaTemplate.send(KafkaTopics.TASK_SUBMIT, task.id, TaskSubmitMessage(task.id, task.imageUrl)).get()

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            taskRepository.findById(task.id)?.state shouldBe TaskStatus.SUBMITTED
        }

        val saved = taskRepository.findById(task.id)!!
        saved.state shouldBe TaskStatus.SUBMITTED
        saved.jobId shouldBe "job-success"
        saved.nextPollAt.shouldNotBeNull()
    }

    @Test
    fun `should transition to FAILED on non-retryable failure`() {
        val task = createAndSaveTask()

        every { imageProcessor.submitImage(task.imageUrl) } returns
            SubmitResult.NonRetryableFailure(reason = "Invalid image format")

        kafkaTemplate.send(KafkaTopics.TASK_SUBMIT, task.id, TaskSubmitMessage(task.id, task.imageUrl)).get()

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            taskRepository.findById(task.id)?.state shouldBe TaskStatus.FAILED
        }

        val saved = taskRepository.findById(task.id)!!
        saved.state shouldBe TaskStatus.FAILED
        saved.failReason shouldBe "Invalid image format"
    }

    @Test
    fun `should retry on retryable failure and eventually reach DLT`() {
        val task = createAndSaveTask()

        every { imageProcessor.submitImage(task.imageUrl) } returns
            SubmitResult.RetryableFailure(reason = "Connection timeout")

        kafkaTemplate.send(KafkaTopics.TASK_SUBMIT, task.id, TaskSubmitMessage(task.id, task.imageUrl)).get()

        // RetryableTopic attempts=4, backOff delay=2000, multiplier=2.0
        // After all retries exhausted -> DLT -> FAILED
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            taskRepository.findById(task.id)?.state shouldBe TaskStatus.FAILED
        }

        val saved = taskRepository.findById(task.id)!!
        saved.state shouldBe TaskStatus.FAILED
        saved.failReason shouldBe "Submit failed after all retries (DLT)"
    }

    @Test
    fun `should record circuit breaker failure on retryable failure`() {
        val task = createAndSaveTask()

        every { imageProcessor.submitImage(task.imageUrl) } returns
            SubmitResult.RetryableFailure(reason = "Server error")

        kafkaTemplate.send(KafkaTopics.TASK_SUBMIT, task.id, TaskSubmitMessage(task.id, task.imageUrl)).get()

        // After failure-threshold (3) retryable failures from retries, circuit should open
        await().atMost(Duration.ofSeconds(20)).until { circuitBreaker.isOpen() }

        circuitBreaker.isOpen() shouldBe true
    }

    @Test
    fun `should open circuit breaker on 429 with retry-after`() {
        val task = createAndSaveTask()

        every { imageProcessor.submitImage(task.imageUrl) } returns
            SubmitResult.RetryableFailure(reason = "Rate limited", retryAfterSeconds = 5)

        kafkaTemplate.send(KafkaTopics.TASK_SUBMIT, task.id, TaskSubmitMessage(task.id, task.imageUrl)).get()

        // 429 should immediately open the circuit via openForSeconds
        await().atMost(Duration.ofSeconds(10)).until { circuitBreaker.isOpen() }

        circuitBreaker.isOpen() shouldBe true
    }

    @Test
    fun `should reset circuit breaker on success after failures`() {
        val failTask = createAndSaveTask()

        every { imageProcessor.submitImage(failTask.imageUrl) } returns
            SubmitResult.RetryableFailure(reason = "Server error")

        kafkaTemplate.send(KafkaTopics.TASK_SUBMIT, failTask.id, TaskSubmitMessage(failTask.id, failTask.imageUrl)).get()

        // Wait for at least one failure to be recorded
        Thread.sleep(3000)

        // Now send a successful task
        val successTask = createAndSaveTask()

        every { imageProcessor.submitImage(successTask.imageUrl) } returns
            SubmitResult.Success(jobId = "job-reset")

        kafkaTemplate.send(KafkaTopics.TASK_SUBMIT, successTask.id, TaskSubmitMessage(successTask.id, successTask.imageUrl)).get()

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            taskRepository.findById(successTask.id)?.state shouldBe TaskStatus.SUBMITTED
        }

        circuitBreaker.isOpen() shouldBe false
    }

    companion object {
        private val idCounter = AtomicInteger(0)
    }
}
