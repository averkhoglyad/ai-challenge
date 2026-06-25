package io.averkhogliad.ai.challenge.week2.infrastructure.persistence

import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStepId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.*

/**
 * Тесты для [SqliteTaskStepRepository].
 *
 * Используют временный файл базы данных для каждого теста,
 * который удаляется после завершения теста.
 */
@DisplayName("SqliteTaskStepRepository")
class SqliteTaskStepRepositoryTest {

    private lateinit var tempDbFile: File
    private lateinit var database: SqliteDatabase
    private lateinit var repository: SqliteTaskStepRepository

    @BeforeEach
    fun setUp() {
        tempDbFile = Files.createTempFile("test-taskstep-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteTaskStepRepository(database)
    }

    @AfterEach
    fun tearDown() {
        database.close()
        tempDbFile.delete()

        // Удаляем WAL и SHM файлы, если они существуют
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    @Nested
    @DisplayName("save() и findById()")
    inner class SaveAndFindById {

        @Test
        @DisplayName("should save and find step by id")
        fun `should save and find step by id`() {
            val step = createTestStep("step-1", "task-1", "Implement login")

            repository.save(step)
            val found = repository.findById(step.id)

            assertNotNull(found)
            assertEquals(step.id, found.id)
            assertEquals(step.taskId, found.taskId)
            assertEquals(step.text, found.text)
            assertEquals(step.isCompleted, found.isCompleted)
            assertEquals(step.order, found.order)
        }

        @Test
        @DisplayName("should return null when step not found")
        fun `should return null when step not found`() {
            val nonExistentId = TaskStepId("non-existent-step")
            val found = repository.findById(nonExistentId)

            assertNull(found)
        }

        @Test
        @DisplayName("should update step on save (upsert)")
        fun `should update step on save`() {
            val step = createTestStep("step-1", "task-1", "Original text")

            repository.save(step)

            val updatedStep = step.markCompleted().updateText("Updated text")
            repository.save(updatedStep)

            val found = repository.findById(step.id)
            assertNotNull(found)
            assertEquals("Updated text", found.text)
            assertTrue(found.isCompleted)
        }
    }

    @Nested
    @DisplayName("findByTaskId() с сортировкой по order")
    inner class FindByTaskId {

        @Test
        @DisplayName("should find all steps for task sorted by order")
        fun `should find all steps for task sorted by order`() {
            val taskId = TaskId("task-1")
            val step1 = createTestStep("step-1", taskId.value, "First step", order = 2)
            val step2 = createTestStep("step-2", taskId.value, "Second step", order = 0)
            val step3 = createTestStep("step-3", taskId.value, "Third step", order = 1)

            repository.save(step1)
            repository.save(step2)
            repository.save(step3)

            val steps = repository.findByTaskId(taskId)

            assertEquals(3, steps.size)
            assertEquals("Second step", steps[0].text) // order=0
            assertEquals("Third step", steps[1].text)  // order=1
            assertEquals("First step", steps[2].text)  // order=2
        }

        @Test
        @DisplayName("should return empty list when no steps for task")
        fun `should return empty list when no steps for task`() {
            val taskId = TaskId("non-existent-task")
            val steps = repository.findByTaskId(taskId)

            assertTrue(steps.isEmpty())
        }

        @Test
        @DisplayName("should return only steps for the specified taskId")
        fun `should return only steps for specified taskId`() {
            val taskId1 = TaskId("task-1")
            val taskId2 = TaskId("task-2")
            val step1 = createTestStep("step-1", taskId1.value, "Task 1 step", order = 0)
            val step2 = createTestStep("step-2", taskId2.value, "Task 2 step", order = 0)

            repository.save(step1)
            repository.save(step2)

            val steps = repository.findByTaskId(taskId1)
            assertEquals(1, steps.size)
            assertEquals("Task 1 step", steps[0].text)
        }
    }

    @Nested
    @DisplayName("delete()")
    inner class Delete {

        @Test
        @DisplayName("should delete step by id and return true")
        fun `should delete step by id`() {
            val step = createTestStep("step-1", "task-1", "To be deleted")

            repository.save(step)
            val deleted = repository.delete(step.id)

            assertTrue(deleted)
            val found = repository.findById(step.id)
            assertNull(found)
        }

        @Test
        @DisplayName("should return false when deleting non-existent step")
        fun `should return false when deleting non-existent step`() {
            val nonExistentId = TaskStepId("non-existent-step")
            val deleted = repository.delete(nonExistentId)

            assertFalse(deleted)
        }
    }

    @Nested
    @DisplayName("deleteByTaskId()")
    inner class DeleteByTaskId {

        @Test
        @DisplayName("should delete all steps for task")
        fun `should delete all steps for task`() {
            val taskId = TaskId("task-1")
            val step1 = createTestStep("step-1", taskId.value, "Step 1", order = 0)
            val step2 = createTestStep("step-2", taskId.value, "Step 2", order = 1)
            val step3 = createTestStep("step-3", taskId.value, "Step 3", order = 2)

            repository.save(step1)
            repository.save(step2)
            repository.save(step3)

            val deletedCount = repository.deleteByTaskId(taskId)

            assertEquals(3, deletedCount)
            val steps = repository.findByTaskId(taskId)
            assertTrue(steps.isEmpty())
        }

        @Test
        @DisplayName("should return 0 when deleting non-existent task")
        fun `should return 0 when deleting non-existent task`() {
            val taskId = TaskId("non-existent-task")
            val deletedCount = repository.deleteByTaskId(taskId)

            assertEquals(0, deletedCount)
        }

        @Test
        @DisplayName("should delete only steps for the specified taskId")
        fun `should delete only steps for specified taskId`() {
            val taskId1 = TaskId("task-1")
            val taskId2 = TaskId("task-2")
            val step1 = createTestStep("step-1", taskId1.value, "Task 1", order = 0)
            val step2 = createTestStep("step-2", taskId2.value, "Task 2", order = 0)

            repository.save(step1)
            repository.save(step2)

            repository.deleteByTaskId(taskId1)

            val steps1 = repository.findByTaskId(taskId1)
            assertTrue(steps1.isEmpty())

            val steps2 = repository.findByTaskId(taskId2)
            assertEquals(1, steps2.size)
            assertEquals("Task 2", steps2[0].text)
        }
    }

    @Nested
    @DisplayName("countByTaskId()")
    inner class CountByTaskId {

        @Test
        @DisplayName("should count steps for task")
        fun `should count steps for task`() {
            val taskId = TaskId("task-1")
            val step1 = createTestStep("step-1", taskId.value, "Step 1", order = 0)
            val step2 = createTestStep("step-2", taskId.value, "Step 2", order = 1)

            repository.save(step1)
            repository.save(step2)

            val count = repository.countByTaskId(taskId)
            assertEquals(2, count)
        }

        @Test
        @DisplayName("should return 0 when task has no steps")
        fun `should return 0 when task has no steps`() {
            val taskId = TaskId("empty-task")
            val count = repository.countByTaskId(taskId)

            assertEquals(0, count)
        }

        @Test
        @DisplayName("should reflect changes after delete")
        fun `should reflect changes after delete`() {
            val taskId = TaskId("task-1")
            val step = createTestStep("step-1", taskId.value, "Step", order = 0)

            repository.save(step)
            assertEquals(1, repository.countByTaskId(taskId))

            repository.delete(step.id)
            assertEquals(0, repository.countByTaskId(taskId))
        }
    }

    private fun createTestStep(
        stepId: String,
        taskId: String,
        text: String,
        isCompleted: Boolean = false,
        order: Int = 0
    ): TaskStep = TaskStep(
        id = TaskStepId(stepId),
        taskId = TaskId(taskId),
        text = text,
        isCompleted = isCompleted,
        order = order,
        createdAt = Instant.now()
    )
}
