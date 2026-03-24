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
        state: TaskStatus = TaskStatus.PENDING,
        jobId: String? = null,
        result: String? = null,
        failReason: String? = null,
        retryCount: Int = 0,
        pollCount: Int = 0,
        nextPollAt: Instant? = null,
        version: Long = 0,
    ): Task =
        Task(
            id = id,
            imageUrl = imageUrl,
            state = state,
            jobId = jobId,
            result = result,
            failReason = failReason,
            retryCount = retryCount,
            pollCount = pollCount,
            nextPollAt = nextPollAt,
            version = version,
        )

    fun random(): Task = fixtureMonkey.giveMeOne<Task>()

    fun builder() = fixtureMonkey.giveMeBuilder<Task>()

    fun submitted(
        id: String = "01H5N0640J7Q",
        jobId: String = "job-1",
        nextPollAt: Instant? = Instant.now().plusSeconds(10),
    ): Task = create(id = id, state = TaskStatus.SUBMITTED, jobId = jobId, nextPollAt = nextPollAt)

    fun processing(
        id: String = "01H5N0640J7Q",
        jobId: String = "job-1",
        nextPollAt: Instant? = Instant.now().plusSeconds(10),
    ): Task = create(id = id, state = TaskStatus.PROCESSING, jobId = jobId, nextPollAt = nextPollAt)
}
