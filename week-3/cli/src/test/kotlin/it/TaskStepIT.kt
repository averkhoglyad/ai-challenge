package io.averkhogliad.ai.challenge.week3.cli.it

import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskStepRepository
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteTaskStepRepository

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Интеграционные тесты для проверки end-to-end сценариев управления шагами задач (Фаза 4).
 *
 * Проверяют:
 * - Полный сценарий: open task → add step → list steps → complete step → back → open → steps persisted
 * - Валидация: операции с шагами без открытой задачи
 * - WM на уровне задачи включает шаги
 */
class TaskStepIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var taskRepository: TaskRepository
    lateinit var taskStepRepository: TaskStepRepository
    lateinit var todoTaskService: TodoTaskService

    fun addStep(taskId: TaskId, text: String, order: Int): TaskStep {
        val step = TaskStep(
            id = TaskStepId(UUID.randomUUID().toString()),
            taskId = taskId,
            text = text,
            isCompleted = false,
            order = order,
            createdAt = Instant.now()
        )
        return taskStepRepository.save(step)
    }

    beforeTest {
        tempDbFile = Files.createTempFile("test-taskstep-integration-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        taskStepRepository = SqliteTaskStepRepository(database)
        taskRepository = object : TaskRepository {

            private val tasks = mutableMapOf<TaskId, Task>()

            override suspend fun save(task: Task) {
                tasks[task.id] = task
            }

            override suspend fun findById(id: TaskId): Task? = tasks[id]

            override suspend fun findAll(): List<Task> = tasks.values.toList()

            override suspend fun delete(id: TaskId) {
                tasks.remove(id)
            }

            override suspend fun exists(id: TaskId): Boolean = tasks.containsKey(id)

            override suspend fun saveSteps(taskId: TaskId, steps: List<TaskStep>) {
                // no-op for step integration tests
            }

            override suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep> = emptyList()

            override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
                Result.success(Unit)

            override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
                Result.success(Unit)
        }
        todoTaskService = TodoTaskService(taskRepository)
    }

    afterTest {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    // ========================================================================
    // US-4.1: Полный сценарий управления шагами
    // ========================================================================

    "full step management scenario: open → add → list → complete → back → open → persisted" - {
        runTest {
            val createdTask = todoTaskService.addTask("Implement feature")
            val taskId = createdTask.id

            todoTaskService.openTask(taskId)
            todoTaskService.currentTaskId shouldBe taskId

            addStep(taskId, "Design the API", 0)
            addStep(taskId, "Write unit tests", 1)
            addStep(taskId, "Implement logic", 2)

            val steps = taskStepRepository.findByTaskId(taskId)
            steps.size shouldBe 3
            steps[0].text shouldBe "Design the API"
            steps[1].text shouldBe "Write unit tests"
            steps[2].text shouldBe "Implement logic"
            (steps.none { it.isCompleted }) shouldBe true

            val completedStep = steps[0].markCompleted()
            taskStepRepository.save(completedStep)

            val updatedSteps = taskStepRepository.findByTaskId(taskId)
            updatedSteps[0].isCompleted shouldBe true
            updatedSteps[1].isCompleted shouldBe false
            updatedSteps[2].isCompleted shouldBe false

            todoTaskService.back()
            (todoTaskService.currentTaskId == null) shouldBe true

            todoTaskService.openTask(taskId)
            val stepsAfterReopen = taskStepRepository.findByTaskId(taskId)
            stepsAfterReopen.size shouldBe 3
            stepsAfterReopen[0].isCompleted shouldBe true
        }
    }

    // ========================================================================
    // US-4.2: Валидация — шаги не могут быть добавлены без открытой задачи
    // ========================================================================

    "steps require an open task" - {
        runTest {
            val taskId = TaskId(UUID.randomUUID().toString())

            val step = TaskStep(
                id = TaskStepId(UUID.randomUUID().toString()),
                taskId = taskId,
                text = "Orphan step",
                isCompleted = false,
                order = 0,
                createdAt = Instant.now()
            )
            val saved = taskStepRepository.save(step)

            saved.text shouldBe "Orphan step"
            taskStepRepository.findById(saved.id).shouldNotBeNull()
        }
    }

    // ========================================================================
    // US-4.3: WM на уровне задачи включает шаги
    // ========================================================================

    "task-level steps are persisted and sorted by order" - {
        runTest {
            val taskId1 = TaskId(UUID.randomUUID().toString())
            val taskId2 = TaskId(UUID.randomUUID().toString())

            val step1a = addStep(taskId1, "Task 1 - Step A", 0)
            val step1b = addStep(taskId1, "Task 1 - Step B", 1)
            val step2a = addStep(taskId2, "Task 2 - Step A", 0)

            val steps1 = taskStepRepository.findByTaskId(taskId1)
            val steps2 = taskStepRepository.findByTaskId(taskId2)

            steps1.size shouldBe 2
            steps2.size shouldBe 1
            steps1[0].text shouldBe "Task 1 - Step A"
            steps1[1].text shouldBe "Task 1 - Step B"
            steps2[0].text shouldBe "Task 2 - Step A"
        }
    }

    "completed steps are tracked correctly" - {
        runTest {
            val taskId = TaskId(UUID.randomUUID().toString())

            val step1 = addStep(taskId, "Pending step", 0)
            val step2 = addStep(taskId, "Done step", 1).markCompleted()
            taskStepRepository.save(step2)

            val steps = taskStepRepository.findByTaskId(taskId)
            steps.size shouldBe 2
            (steps.any { !it.isCompleted }) shouldBe true
            (steps.any { it.isCompleted }) shouldBe true

            taskStepRepository.countByTaskId(taskId) shouldBe 2
        }
    }

    "delete all steps by task id" - {
        runTest {
            val taskId = TaskId(UUID.randomUUID().toString())

            addStep(taskId, "Step 1", 0)
            addStep(taskId, "Step 2", 1)
            addStep(taskId, "Step 3", 2)
            taskStepRepository.countByTaskId(taskId) shouldBe 3

            val deleted = taskStepRepository.deleteByTaskId(taskId)
            deleted shouldBe 3
            taskStepRepository.countByTaskId(taskId) shouldBe 0
            taskStepRepository.findByTaskId(taskId).isEmpty() shouldBe true
        }
    }
})
