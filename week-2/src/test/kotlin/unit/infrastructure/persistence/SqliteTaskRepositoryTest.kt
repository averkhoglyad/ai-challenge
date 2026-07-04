package io.averkhogliad.ai.challenge.week2.unit.infrastructure.persistence

import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteTaskRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.Instant

class SqliteTaskRepositoryTest : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteTaskRepository

    beforeEach {
        tempDbFile = Files.createTempFile("test-task-repo-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteTaskRepository(database)
    }

    afterEach {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "save and findById" - {

        "should save and find task by id" {
            runTest {
                val task = createTask("task-1", "Test Task")
                repository.save(task)

                val found = repository.findById(TaskId("task-1"))
                found.shouldNotBeNull()
                found.id shouldBe task.id
                found.title shouldBe task.title
                found.status shouldBe task.status
                found.createdAt shouldBe task.createdAt
                found.updatedAt shouldBe task.updatedAt
            }
        }

        "should return null when task not found" {
            runTest {
                val found = repository.findById(TaskId("non-existent"))
                found.shouldBeNull()
            }
        }

        "should update task on save (upsert)" {
            runTest {
                val task = createTask("task-1", "Original Title")
                repository.save(task)

                val updatedTask = task.updateTitle("Updated Title")
                repository.save(updatedTask)

                val found = repository.findById(TaskId("task-1"))
                found.shouldNotBeNull()
                found.title shouldBe "Updated Title"
            }
        }
    }

    "findAll" - {

        "should find all tasks" {
            runTest {
                repository.save(createTask("task-1", "Task 1"))
                repository.save(createTask("task-2", "Task 2"))
                repository.save(createTask("task-3", "Task 3"))

                val all = repository.findAll()
                all shouldHaveSize 3
            }
        }

        "should return empty list when no tasks" {
            runTest {
                val all = repository.findAll()
                all.shouldBeEmpty()
            }
        }
    }

    "delete" - {

        "should delete task" {
            runTest {
                val task = createTask("task-1", "Test Task")
                repository.save(task)
                repository.exists(TaskId("task-1")) shouldBe true

                repository.delete(TaskId("task-1"))

                repository.exists(TaskId("task-1")) shouldBe false
                repository.findById(TaskId("task-1")).shouldBeNull()
            }
        }

        "should handle delete of non-existent task" {
            runTest {
                repository.delete(TaskId("non-existent"))
            }
        }
    }

    "exists" - {

        "should check if task exists" {
            runTest {
                repository.save(createTask("task-1", "Test Task"))

                repository.exists(TaskId("task-1")) shouldBe true
                repository.exists(TaskId("non-existent")) shouldBe false
            }
        }
    }

    "status persistence" - {

        "should handle different task statuses" {
            runTest {
                repository.save(createTask("open", "Open Task", TaskStatus.OPEN))
                repository.save(createTask("closed", "Closed Task", TaskStatus.CLOSED))
                repository.save(createTask("cancelled", "Cancelled Task", TaskStatus.CANCELLED))

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
                found.shouldNotBeNull()
                found.status shouldBe TaskStatus.CLOSED
            }
        }

        "should persist task with cancelled status" {
            runTest {
                val task = createTask("task-1", "Test Task", TaskStatus.CANCELLED)
                repository.save(task)

                val found = repository.findById(TaskId("task-1"))
                found.shouldNotBeNull()
                found.status shouldBe TaskStatus.CANCELLED
            }
        }
    }

    "steps via saveSteps and findStepsByTaskId" - {

        "should save and find steps by task id" {
            runTest {
                val task = createTask("task-1", "Test Task")
                repository.save(task)

                val steps = listOf(
                    TaskStep(TaskStepId("step-1"), TaskId("task-1"), "First step", false, 1, Instant.now()),
                    TaskStep(TaskStepId("step-2"), TaskId("task-1"), "Second step", false, 2, Instant.now())
                )
                repository.saveSteps(TaskId("task-1"), steps)

                val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
                foundSteps shouldHaveSize 2
                foundSteps[0].text shouldBe "First step"
                foundSteps[1].text shouldBe "Second step"
                foundSteps[0].order shouldBe 1
                foundSteps[1].order shouldBe 2
            }
        }

        "should return empty list when no steps for task" {
            runTest {
                repository.save(createTask("task-1", "Test Task"))

                val steps = repository.findStepsByTaskId(TaskId("task-1"))
                steps.shouldBeEmpty()
            }
        }

        "should replace steps when saving again" {
            runTest {
                repository.save(createTask("task-1", "Test Task"))

                repository.saveSteps(
                    TaskId("task-1"), listOf(
                        TaskStep(TaskStepId("step-1"), TaskId("task-1"), "Initial step", false, 1, Instant.now())
                    )
                )

                repository.saveSteps(
                    TaskId("task-1"), listOf(
                        TaskStep(TaskStepId("step-2"), TaskId("task-1"), "New step 1", false, 1, Instant.now()),
                        TaskStep(TaskStepId("step-3"), TaskId("task-1"), "New step 2", false, 2, Instant.now())
                    )
                )

                val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
                foundSteps shouldHaveSize 2
                foundSteps[0].text shouldBe "New step 1"
                foundSteps[1].text shouldBe "New step 2"
            }
        }

        "should save steps with completed status" {
            runTest {
                repository.save(createTask("task-1", "Test Task"))

                repository.saveSteps(
                    TaskId("task-1"), listOf(
                        TaskStep(TaskStepId("step-1"), TaskId("task-1"), "Completed step", true, 1, Instant.now())
                    )
                )

                val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
                foundSteps shouldHaveSize 1
                foundSteps[0].isCompleted shouldBe true
            }
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
