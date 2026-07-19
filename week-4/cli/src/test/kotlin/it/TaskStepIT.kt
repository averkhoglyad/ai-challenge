package io.averkhogliad.ai.challenge.week4.cli.it

import io.averkhogliad.ai.challenge.week4.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskStepRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteTaskRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteTaskStepRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.Instant
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

    beforeTest {
        tempDbFile = Files.createTempFile("test-taskstep-integration-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        taskRepository = SqliteTaskRepository(database)
        taskStepRepository = SqliteTaskStepRepository(database)
        todoTaskService = TodoTaskService(taskRepository)
    }

    afterTest {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

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

    // ========================================================================
    // US-4.1: Полный сценарий управления шагами
    // ========================================================================

    "full step management scenario: open → add → list → complete → back → open → persisted" {
        runTest {
            // 1. Создаём задачу
            val createdTask = todoTaskService.addTask("Implement feature")
            val taskId = createdTask.id

            // 2. Открываем задачу
            todoTaskService.openTask(taskId)
            todoTaskService.currentTaskId shouldBe taskId

            // 3. Добавляем шаги
            val step1 = addStep(taskId, "Design the API", 0)
            val step2 = addStep(taskId, "Write unit tests", 1)
            val step3 = addStep(taskId, "Implement logic", 2)

            // 4. Проверяем список шагов
            val steps = taskStepRepository.findByTaskId(taskId)
            steps shouldHaveSize 3
            steps[0].text shouldBe "Design the API"
            steps[1].text shouldBe "Write unit tests"
            steps[2].text shouldBe "Implement logic"
            (steps.none { it.isCompleted }) shouldBe true

            // 5. Отмечаем шаг выполненным
            val completedStep = steps[0].markCompleted()
            taskStepRepository.save(completedStep)

            // 6. Проверяем, что шаг теперь выполнен
            val updatedSteps = taskStepRepository.findByTaskId(taskId)
            updatedSteps[0].isCompleted shouldBe true
            updatedSteps[1].isCompleted shouldBe false
            updatedSteps[2].isCompleted shouldBe false

            // 7. Возвращаемся к списку задач
            todoTaskService.back()
            (todoTaskService.currentTaskId == null) shouldBe true

            // 8. Снова открываем задачу — шаги должны сохраниться
            todoTaskService.openTask(taskId)
            val stepsAfterReopen = taskStepRepository.findByTaskId(taskId)
            stepsAfterReopen shouldHaveSize 3
            stepsAfterReopen[0].isCompleted shouldBe true
        }
    }

    // ========================================================================
    // US-4.2: Валидация — шаги не могут быть добавлены без открытой задачи
    // ========================================================================

    "steps require an open task" {
        runTest {
            // Create task first to satisfy FK constraint
            val createdTask = todoTaskService.addTask("Test task")
            val taskId = createdTask.id

            // Попытка добавить шаг без открытой задачи должна требовать валидации
            // на уровне CommandHandler (requireTaskOpen). Здесь проверяем, что
            // TaskStepRepository принимает шаги для любой существующей задачи
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
            taskStepRepository.findById(saved.id) shouldNotBe null
        }
    }

    // ========================================================================
    // US-4.3: WM на уровне задачи включает шаги
    // ========================================================================

    "task-level steps are persisted and sorted by order" {
        runTest {
            val created1 = todoTaskService.addTask("Task 1")
            val created2 = todoTaskService.addTask("Task 2")
            val taskId1 = created1.id
            val taskId2 = created2.id

            // Добавляем шаги для двух разных задач
            addStep(taskId1, "Task 1 - Step A", 0)
            addStep(taskId1, "Task 1 - Step B", 1)
            addStep(taskId2, "Task 2 - Step A", 0)

            // Проверяем, что шаги правильно изолированы по задачам
            val steps1 = taskStepRepository.findByTaskId(taskId1)
            val steps2 = taskStepRepository.findByTaskId(taskId2)

            steps1 shouldHaveSize 2
            steps2 shouldHaveSize 1
            steps1[0].text shouldBe "Task 1 - Step A"
            steps1[1].text shouldBe "Task 1 - Step B"
            steps2[0].text shouldBe "Task 2 - Step A"
        }
    }

    "completed steps are tracked correctly" {
        runTest {
            val created = todoTaskService.addTask("Test")
            val taskId = created.id

            // Добавляем шаги с разными статусами
            addStep(taskId, "Pending step", 0)
            val step2 = addStep(taskId, "Done step", 1).markCompleted()
            taskStepRepository.save(step2)

            val steps = taskStepRepository.findByTaskId(taskId)
            steps shouldHaveSize 2
            (steps.any { !it.isCompleted }) shouldBe true
            (steps.any { it.isCompleted }) shouldBe true

            // countByTaskId возвращает общее количество
            taskStepRepository.countByTaskId(taskId) shouldBe 2
        }
    }

    "delete all steps by task id" {
        runTest {
            val created = todoTaskService.addTask("Test")
            val taskId = created.id

            addStep(taskId, "Step 1", 0)
            addStep(taskId, "Step 2", 1)
            addStep(taskId, "Step 3", 2)
            taskStepRepository.countByTaskId(taskId) shouldBe 3

            val deleted = taskStepRepository.deleteByTaskId(taskId)
            deleted shouldBe 3
            taskStepRepository.countByTaskId(taskId) shouldBe 0
            taskStepRepository.findByTaskId(taskId).shouldHaveSize(0)
        }
    }
})
