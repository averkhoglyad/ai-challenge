package io.averkhogliad.ai.challenge.week4.cli.it.persistence

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteTaskRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Интеграционные тесты для [SqliteTaskRepository].
 * Использует временный файл для SQLite базы данных.
 */
class SqliteTaskRepositoryIT : FreeSpec({

    lateinit var tempDir: Path
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteTaskRepository
    lateinit var dbPath: String

    beforeEach {
        tempDir = Files.createTempDirectory("test-tasks-")
        dbPath = tempDir.resolve("test-tasks.db").toString()
        database = SqliteDatabase(dbPath)
        repository = SqliteTaskRepository(database)
    }

    afterEach {
        database.close()
    }

    "should save and find task by id" {
        runTest {
            val task = createTask("task-1", "Test Task")
            repository.save(task)

            val found = repository.findById(TaskId("task-1"))
            (found != null) shouldBe true
            found!!.id shouldBe task.id
            found.title shouldBe task.title
            found.status shouldBe task.status
            found.createdAt shouldBe task.createdAt
            found.updatedAt shouldBe task.updatedAt
        }
    }

    "should return null when task not found" {
        runTest {
            val found = repository.findById(TaskId("non-existent"))
            found shouldBe null
        }
    }

    "should find all tasks" {
        runTest {
            val task1 = createTask("task-1", "Task 1")
            val task2 = createTask("task-2", "Task 2")
            val task3 = createTask("task-3", "Task 3")

            repository.save(task1)
            repository.save(task2)
            repository.save(task3)

            val all = repository.findAll()
            all.size shouldBe 3
        }
    }

    "should delete task" {
        runTest {
            val task = createTask("task-1", "Test Task")
            repository.save(task)

            repository.exists(TaskId("task-1")) shouldBe true

            repository.delete(TaskId("task-1"))

            repository.exists(TaskId("task-1")) shouldBe false
            repository.findById(TaskId("task-1")) shouldBe null
        }
    }

    "should check if task exists" {
        runTest {
            val task = createTask("task-1", "Test Task")
            repository.save(task)

            repository.exists(TaskId("task-1")) shouldBe true
            repository.exists(TaskId("non-existent")) shouldBe false
        }
    }

    "should update task on save" {
        runTest {
            val task = createTask("task-1", "Original Title")
            repository.save(task)

            val updatedTask = task.updateTitle("Updated Title")
            repository.save(updatedTask)

            val found = repository.findById(TaskId("task-1"))
            (found != null) shouldBe true
            found!!.title shouldBe "Updated Title"
        }
    }

    "should handle different task statuses" {
        runTest {
            val openTask = createTask("open", "Open Task", TaskStatus.OPEN)
            val closedTask = createTask("closed", "Closed Task", TaskStatus.CLOSED)
            val cancelledTask = createTask("cancelled", "Cancelled Task", TaskStatus.CANCELLED)

            repository.save(openTask)
            repository.save(closedTask)
            repository.save(cancelledTask)

            repository.findById(TaskId("open"))?.status shouldBe TaskStatus.OPEN
            repository.findById(TaskId("closed"))?.status shouldBe TaskStatus.CLOSED
            repository.findById(TaskId("cancelled"))?.status shouldBe TaskStatus.CANCELLED
        }
    }

    "should persist task with closed status" {
        runTest {
            val task = createTask("task-1", "Test Task", TaskStatus.CLOSED)
            repository.save(task)

            val found = repository.findById(TaskId("task-1"))
            (found != null) shouldBe true
            found!!.status shouldBe TaskStatus.CLOSED
        }
    }

    "should persist task with cancelled status" {
        runTest {
            val task = createTask("task-1", "Test Task", TaskStatus.CANCELLED)
            repository.save(task)

            val found = repository.findById(TaskId("task-1"))
            (found != null) shouldBe true
            found!!.status shouldBe TaskStatus.CANCELLED
        }
    }

    "should return empty list when no tasks" {
        runTest {
            val all = repository.findAll()
            all.isEmpty() shouldBe true
        }
    }

    "should handle delete of non-existent task" {
        runTest {
            // Не должно выбрасывать исключение
            repository.delete(TaskId("non-existent"))
        }
    }

    "should save and find steps by task id" {
        runTest {
            val task = createTask("task-1", "Test Task")
            repository.save(task)

            val steps = listOf(
                TaskStep(
                    id = TaskStepId("step-1"),
                    taskId = TaskId("task-1"),
                    text = "First step",
                    isCompleted = false,
                    order = 1,
                    createdAt = Instant.now()
                ),
                TaskStep(
                    id = TaskStepId("step-2"),
                    taskId = TaskId("task-1"),
                    text = "Second step",
                    isCompleted = false,
                    order = 2,
                    createdAt = Instant.now()
                )
            )

            repository.saveSteps(TaskId("task-1"), steps)

            val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
            foundSteps.size shouldBe 2
            foundSteps[0].text shouldBe "First step"
            foundSteps[1].text shouldBe "Second step"
            foundSteps[0].order shouldBe 1
            foundSteps[1].order shouldBe 2
        }
    }

    "should return empty list when no steps for task" {
        runTest {
            val task = createTask("task-1", "Test Task")
            repository.save(task)

            val steps = repository.findStepsByTaskId(TaskId("task-1"))
            steps.isEmpty() shouldBe true
        }
    }

    "should replace steps when saving again" {
        runTest {
            val task = createTask("task-1", "Test Task")
            repository.save(task)

            val initialSteps = listOf(
                TaskStep(
                    id = TaskStepId("step-1"),
                    taskId = TaskId("task-1"),
                    text = "Initial step",
                    isCompleted = false,
                    order = 1,
                    createdAt = Instant.now()
                )
            )
            repository.saveSteps(TaskId("task-1"), initialSteps)

            val newSteps = listOf(
                TaskStep(
                    id = TaskStepId("step-2"),
                    taskId = TaskId("task-1"),
                    text = "New step 1",
                    isCompleted = false,
                    order = 1,
                    createdAt = Instant.now()
                ),
                TaskStep(
                    id = TaskStepId("step-3"),
                    taskId = TaskId("task-1"),
                    text = "New step 2",
                    isCompleted = false,
                    order = 2,
                    createdAt = Instant.now()
                )
            )
            repository.saveSteps(TaskId("task-1"), newSteps)

            val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
            foundSteps.size shouldBe 2
            foundSteps[0].text shouldBe "New step 1"
            foundSteps[1].text shouldBe "New step 2"
        }
    }

    "should save steps with completed status" {
        runTest {
            val task = createTask("task-1", "Test Task")
            repository.save(task)

            val steps = listOf(
                TaskStep(
                    id = TaskStepId("step-1"),
                    taskId = TaskId("task-1"),
                    text = "Completed step",
                    isCompleted = true,
                    order = 1,
                    createdAt = Instant.now()
                )
            )

            repository.saveSteps(TaskId("task-1"), steps)

            val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
            foundSteps.size shouldBe 1
            foundSteps[0].isCompleted shouldBe true
        }
    }
})

private fun createTask(
    id: String,
    title: String,
    status: TaskStatus = TaskStatus.OPEN,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
): Task = Task(
    id = TaskId(id),
    title = title,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt
)
