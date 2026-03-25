package com.example.imageprocess.adapter.outbound.persistence

import com.example.imageprocess.TestcontainersConfiguration
import com.example.imageprocess.domain.model.TaskStatus
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import java.time.Instant

@DataJpaTest
@Import(TestcontainersConfiguration::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskJpaRepositoryTest {
    @Autowired
    private lateinit var repository: TaskJpaRepository

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
    }

    private fun createEntity(
        id: String,
        status: TaskStatus = TaskStatus.PENDING,
        nextPollAt: Instant? = null,
        fingerprint: String = "fp-$id",
    ): TaskJpaEntity =
        TaskJpaEntity(
            id = id,
            imageUrl = "https://example.com/$id.png",
            fingerprint = fingerprint,
            status = status,
            nextPollAt = nextPollAt,
        )

    @Test
    fun `save and findById should persist and retrieve task`() {
        val entity = createEntity("task-1")
        repository.save(entity)

        val found = repository.findById("task-1").orElse(null)
        found.shouldNotBeNull()
        found.id shouldBe "task-1"
        found.imageUrl shouldBe "https://example.com/task-1.png"
    }

    @Test
    fun `findByFingerprint should return matching entity`() {
        val entity = createEntity("task-1", fingerprint = "unique-fp")
        repository.save(entity)

        val found = repository.findByFingerprint("unique-fp")
        found.shouldNotBeNull()
        found.id shouldBe "task-1"
    }

    @Test
    fun `findByFingerprint should return null when not found`() {
        repository.findByFingerprint("nonexistent").shouldBeNull()
    }

    @Test
    fun `findPollableTasks should return SUBMITTED tasks with nextPollAt before now`() {
        val now = Instant.now()
        repository.save(createEntity("task-1", TaskStatus.SUBMITTED, now.minusSeconds(10)))
        repository.save(createEntity("task-2", TaskStatus.PENDING, now.minusSeconds(10)))

        val result = repository.findPollableTasks(now, PageRequest.of(0, 10))
        result shouldHaveSize 1
        result[0].id shouldBe "task-1"
    }

    @Test
    fun `findPollableTasks should return PROCESSING tasks with nextPollAt before now`() {
        val now = Instant.now()
        repository.save(createEntity("task-1", TaskStatus.PROCESSING, now.minusSeconds(10)))

        val result = repository.findPollableTasks(now, PageRequest.of(0, 10))
        result shouldHaveSize 1
        result[0].id shouldBe "task-1"
    }

    @Test
    fun `findPollableTasks should exclude tasks with nextPollAt after now`() {
        val now = Instant.now()
        repository.save(createEntity("task-1", TaskStatus.SUBMITTED, now.plusSeconds(60)))

        val result = repository.findPollableTasks(now, PageRequest.of(0, 10))
        result shouldHaveSize 0
    }

    @Test
    fun `findPollableTasks should exclude tasks with null nextPollAt`() {
        repository.save(createEntity("task-1", TaskStatus.SUBMITTED, null))

        val result = repository.findPollableTasks(Instant.now(), PageRequest.of(0, 10))
        result shouldHaveSize 0
    }

    @Test
    fun `findPollableTasks should respect limit`() {
        val now = Instant.now()
        repository.save(createEntity("task-1", TaskStatus.SUBMITTED, now.minusSeconds(30)))
        repository.save(createEntity("task-2", TaskStatus.SUBMITTED, now.minusSeconds(20)))
        repository.save(createEntity("task-3", TaskStatus.SUBMITTED, now.minusSeconds(10)))

        val result = repository.findPollableTasks(now, PageRequest.of(0, 2))
        result shouldHaveSize 2
    }

    @Test
    fun `findByStatusIn should return tasks matching statuses`() {
        repository.save(createEntity("task-1", TaskStatus.PENDING))
        repository.save(createEntity("task-2", TaskStatus.SUBMITTED))
        repository.save(createEntity("task-3", TaskStatus.COMPLETED))

        val result = repository.findByStatusIn(listOf(TaskStatus.PENDING, TaskStatus.SUBMITTED))
        result shouldHaveSize 2
    }

    @Test
    fun `findByStatus should return paginated results`() {
        repository.save(createEntity("task-1", TaskStatus.PENDING))
        repository.save(createEntity("task-2", TaskStatus.PENDING))
        repository.save(createEntity("task-3", TaskStatus.COMPLETED))

        val page = repository.findByStatus(TaskStatus.PENDING, PageRequest.of(0, 10))
        page.totalElements shouldBe 2
        page.content shouldHaveSize 2
    }
}
