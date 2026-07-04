package io.averkhogliad.ai.challenge.week2.it

import io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteTaskRepository
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteTaskStepRepository
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
import java.util.*

class TaskStepIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var taskRepository: SqliteTaskRepository
    lateinit var taskStepRepository: SqliteTaskStepRepository
    lateinit var todoTaskService: TodoTaskService

    beforeEach {
        tempDbFile = Files.createTempFile("test-taskstep-it-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        taskRepository = SqliteTaskRepository(database)
        taskStepRepository = SqliteTaskStepRepository(database)
        todoTaskService = TodoTaskService(taskRepository)
    }

    afterEach {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "step management" - {

        "full scenario: open → add → list → complete → back → open → persisted" {
            runTest {
                val createdTask = todoTaskService.addTask("Implement feature")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)
                todoTaskService.currentTaskId shouldBe taskId

                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Design the API",
                        isCompleted = false, order = 0, createdAt = Instant.now()
                    )
                )
                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Write unit tests",
                        isCompleted = false, order = 1, createdAt = Instant.now()
                    )
                )
                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Implement logic",
                        isCompleted = false, order = 2, createdAt = Instant.now()
                    )
                )

                val steps = taskStepRepository.findByTaskId(taskId)
                steps shouldHaveSize 3
                steps[0].text shouldBe "Design the API"
                steps[1].text shouldBe "Write unit tests"
                steps[2].text shouldBe "Implement logic"
                steps.none { it.isCompleted } shouldBe true

                taskStepRepository.save(steps[0].markCompleted())

                val updatedSteps = taskStepRepository.findByTaskId(taskId)
                updatedSteps[0].isCompleted shouldBe true
                updatedSteps[1].isCompleted shouldBe false
                updatedSteps[2].isCompleted shouldBe false

                todoTaskService.back()
                todoTaskService.currentTaskId.shouldBeNull()

                todoTaskService.openTask(taskId)

                val stepsAfterReopen = taskStepRepository.findByTaskId(taskId)
                stepsAfterReopen shouldHaveSize 3
                stepsAfterReopen[0].isCompleted shouldBe true
            }
        }
    }

    "validation" - {

        "allows saving steps for any task id at repository level" {
            runTest {
                val taskId = TaskId(UUID.randomUUID().toString())

                val step = taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Orphan step",
                        isCompleted = false, order = 0, createdAt = Instant.now()
                    )
                )

                step.text shouldBe "Orphan step"
                taskStepRepository.findById(step.id).shouldNotBeNull()
            }
        }
    }

    "isolation" - {

        "steps are isolated per task and sorted by order" {
            runTest {
                val taskId1 = TaskId(UUID.randomUUID().toString())
                val taskId2 = TaskId(UUID.randomUUID().toString())

                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId1, text = "Task 1 - Step A",
                        isCompleted = false, order = 0, createdAt = Instant.now()
                    )
                )
                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId1, text = "Task 1 - Step B",
                        isCompleted = false, order = 1, createdAt = Instant.now()
                    )
                )
                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId2, text = "Task 2 - Step A",
                        isCompleted = false, order = 0, createdAt = Instant.now()
                    )
                )

                val steps1 = taskStepRepository.findByTaskId(taskId1)
                val steps2 = taskStepRepository.findByTaskId(taskId2)

                steps1 shouldHaveSize 2
                steps2 shouldHaveSize 1
                steps1[0].text shouldBe "Task 1 - Step A"
                steps1[1].text shouldBe "Task 1 - Step B"
                steps2[0].text shouldBe "Task 2 - Step A"
            }
        }

        "tracks completed steps correctly" {
            runTest {
                val taskId = TaskId(UUID.randomUUID().toString())

                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Pending step",
                        isCompleted = false, order = 0, createdAt = Instant.now()
                    )
                )
                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Done step",
                        isCompleted = true, order = 1, createdAt = Instant.now()
                    )
                )

                val steps = taskStepRepository.findByTaskId(taskId)

                steps shouldHaveSize 2
                steps.any { !it.isCompleted } shouldBe true
                steps.any { it.isCompleted } shouldBe true
                taskStepRepository.countByTaskId(taskId) shouldBe 2
            }
        }

        "deletes all steps by task id" {
            runTest {
                val taskId = TaskId(UUID.randomUUID().toString())

                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Step 1",
                        isCompleted = false, order = 0, createdAt = Instant.now()
                    )
                )
                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Step 2",
                        isCompleted = false, order = 1, createdAt = Instant.now()
                    )
                )
                taskStepRepository.save(
                    TaskStep(
                        id = TaskStepId(UUID.randomUUID().toString()),
                        taskId = taskId, text = "Step 3",
                        isCompleted = false, order = 2, createdAt = Instant.now()
                    )
                )
                taskStepRepository.countByTaskId(taskId) shouldBe 3

                val deleted = taskStepRepository.deleteByTaskId(taskId)

                deleted shouldBe 3
                taskStepRepository.countByTaskId(taskId) shouldBe 0
                taskStepRepository.findByTaskId(taskId).shouldBeEmpty()
            }
        }
    }
})
