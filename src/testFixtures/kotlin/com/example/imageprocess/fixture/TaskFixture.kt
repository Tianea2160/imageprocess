package com.example.imageprocess.fixture

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskStatus
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import java.time.Instant

object TaskFixture {
    private val fixtureMonkey =
        FixtureMonkey
            .builder()
            .plugin(KotlinPlugin())
            .build()

    fun create(
        id: String = "01H5N0640J7Q",
        imageUrl: String = "https://example.com/image.png",
        status: TaskStatus = TaskStatus.PENDING,
        jobId: String? = null,
        result: String? = null,
        failReason: String? = null,
        retryCount: Int = 0,
        pollCount: Int = 0,
        nextPollAt: Instant? = null,
        version: Long = 0,
    ): Task {
        val task =
            Task(
                id = id,
                imageUrl = imageUrl,
                status = TaskStatus.PENDING,
                jobId = jobId,
                result = result,
                failReason = failReason,
                retryCount = retryCount,
                pollCount = pollCount,
                nextPollAt = nextPollAt,
                version = version,
            )
        if (status != TaskStatus.PENDING) {
            applyStatus(task, status)
        }
        return task
    }

    fun random(): Task = fixtureMonkey.giveMeOne<Task>()

    fun builder() = fixtureMonkey.giveMeBuilder<Task>()

    fun submitted(
        id: String = "01H5N0640J7Q",
        jobId: String = "job-1",
        nextPollAt: Instant? = Instant.now().plusSeconds(10),
    ): Task = create(id = id, status = TaskStatus.SUBMITTED, jobId = jobId, nextPollAt = nextPollAt)

    fun processing(
        id: String = "01H5N0640J7Q",
        jobId: String = "job-1",
        nextPollAt: Instant? = Instant.now().plusSeconds(10),
    ): Task = create(id = id, status = TaskStatus.PROCESSING, jobId = jobId, nextPollAt = nextPollAt)

    private fun applyStatus(
        task: Task,
        target: TaskStatus,
    ) {
        val path =
            when (target) {
                TaskStatus.PENDING -> emptyList()
                TaskStatus.SUBMITTED -> listOf(TaskStatus.SUBMITTED)
                TaskStatus.PROCESSING -> listOf(TaskStatus.SUBMITTED, TaskStatus.PROCESSING)
                TaskStatus.COMPLETED -> listOf(TaskStatus.SUBMITTED, TaskStatus.PROCESSING, TaskStatus.COMPLETED)
                TaskStatus.FAILED -> listOf(TaskStatus.FAILED)
                TaskStatus.RETRY_WAITING -> listOf(TaskStatus.SUBMITTED, TaskStatus.RETRY_WAITING)
            }
        path.forEach { task.transitionTo(it) }
    }
}
