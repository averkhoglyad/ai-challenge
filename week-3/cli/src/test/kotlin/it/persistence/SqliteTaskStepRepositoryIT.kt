package io.averkhogliad.ai.challenge.week3.cli.it.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteTaskStepRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Тесты для [SqliteTaskStepRepository].
 *
 * Используют временный файл базы данных для каждого теста,
 * который удаляется после завершения теста.
 */
class SqliteTaskStepRepositoryIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteTaskStepRepository

    beforeTest {
        tempDbFile = Files.createTempFile("test-taskstep-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteTaskStepRepository(database)
    }

    afterTest {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    fun createTestStep(
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

    "save() и findById()" - {
        "should save and find step by id" {
            val step = createTestStep("step-1", "task-1", "Implement login")

            repository.save(step)
            val found = repository.findById(step.id)

            found shouldNotBe null
            found!!.id shouldBe step.id
            found.taskId shouldBe step.taskId
            found.text shouldBe step.text
            found.isCompleted shouldBe step.isCompleted
            found.order shouldBe step.order
        }

        "should return null when step not found" {
            val nonExistentId = TaskStepId("non-existent-step")
            val found = repository.findById(nonExistentId)

            found shouldBe null
        }

        "should update step on save (upsert)" {
            val step = createTestStep("step-1", "task-1", "Original text")

            repository.save(step)

            val updatedStep = step.markCompleted().updateText("Updated text")
            repository.save(updatedStep)

            val found = repository.findById(step.id)
            found shouldNotBe null
            found!!.text shouldBe "Updated text"
            found.isCompleted shouldBe true
        }
    }

    "findByTaskId() с сортировкой по order" - {
        "should find all steps for task sorted by order" {
            val taskId = TaskId("task-1")
            val step1 = createTestStep("step-1", taskId.value, "First step", order = 2)
            val step2 = createTestStep("step-2", taskId.value, "Second step", order = 0)
            val step3 = createTestStep("step-3", taskId.value, "Third step", order = 1)

            repository.save(step1)
            repository.save(step2)
            repository.save(step3)

            val steps = repository.findByTaskId(taskId)

            steps shouldHaveSize 3
            steps[0].text shouldBe "Second step" // order=0
            steps[1].text shouldBe "Third step"  // order=1
            steps[2].text shouldBe "First step"  // order=2
        }

        "should return empty list when no steps for task" {
            val taskId = TaskId("non-existent-task")
            val steps = repository.findByTaskId(taskId)

            steps.isEmpty() shouldBe true
        }

        "should return only steps for the specified taskId" {
            val taskId1 = TaskId("task-1")
            val taskId2 = TaskId("task-2")
            val step1 = createTestStep("step-1", taskId1.value, "Task 1 step", order = 0)
            val step2 = createTestStep("step-2", taskId2.value, "Task 2 step", order = 0)

            repository.save(step1)
            repository.save(step2)

            val steps = repository.findByTaskId(taskId1)
            steps shouldHaveSize 1
            steps[0].text shouldBe "Task 1 step"
        }
    }

    "delete()" - {
        "should delete step by id and return true" {
            val step = createTestStep("step-1", "task-1", "To be deleted")

            repository.save(step)
            val deleted = repository.delete(step.id)

            deleted shouldBe true
            val found = repository.findById(step.id)
            found shouldBe null
        }

        "should return false when deleting non-existent step" {
            val nonExistentId = TaskStepId("non-existent-step")
            val deleted = repository.delete(nonExistentId)

            deleted shouldBe false
        }
    }

    "deleteByTaskId()" - {
        "should delete all steps for task" {
            val taskId = TaskId("task-1")
            val step1 = createTestStep("step-1", taskId.value, "Step 1", order = 0)
            val step2 = createTestStep("step-2", taskId.value, "Step 2", order = 1)
            val step3 = createTestStep("step-3", taskId.value, "Step 3", order = 2)

            repository.save(step1)
            repository.save(step2)
            repository.save(step3)

            val deletedCount = repository.deleteByTaskId(taskId)

            deletedCount shouldBe 3
            val steps = repository.findByTaskId(taskId)
            steps.isEmpty() shouldBe true
        }

        "should return 0 when deleting non-existent task" {
            val taskId = TaskId("non-existent-task")
            val deletedCount = repository.deleteByTaskId(taskId)

            deletedCount shouldBe 0
        }

        "should delete only steps for the specified taskId" {
            val taskId1 = TaskId("task-1")
            val taskId2 = TaskId("task-2")
            val step1 = createTestStep("step-1", taskId1.value, "Task 1", order = 0)
            val step2 = createTestStep("step-2", taskId2.value, "Task 2", order = 0)

            repository.save(step1)
            repository.save(step2)

            repository.deleteByTaskId(taskId1)

            val steps1 = repository.findByTaskId(taskId1)
            steps1.isEmpty() shouldBe true

            val steps2 = repository.findByTaskId(taskId2)
            steps2 shouldHaveSize 1
            steps2[0].text shouldBe "Task 2"
        }
    }

    "countByTaskId()" - {
        "should count steps for task" {
            val taskId = TaskId("task-1")
            val step1 = createTestStep("step-1", taskId.value, "Step 1", order = 0)
            val step2 = createTestStep("step-2", taskId.value, "Step 2", order = 1)

            repository.save(step1)
            repository.save(step2)

            val count = repository.countByTaskId(taskId)
            count shouldBe 2
        }

        "should return 0 when task has no steps" {
            val taskId = TaskId("empty-task")
            val count = repository.countByTaskId(taskId)

            count shouldBe 0
        }

        "should reflect changes after delete" {
            val taskId = TaskId("task-1")
            val step = createTestStep("step-1", taskId.value, "Step", order = 0)

            repository.save(step)
            repository.countByTaskId(taskId) shouldBe 1

            repository.delete(step.id)
            repository.countByTaskId(taskId) shouldBe 0
        }
    }
})
