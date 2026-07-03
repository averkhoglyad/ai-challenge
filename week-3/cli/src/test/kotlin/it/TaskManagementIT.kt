package io.averkhogliad.ai.challenge.week3.cli.it

import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.*

/**
 * Integration-тесты для проверки end-to-end сценариев управления задачами.
 *
 * Покрывают пользовательские истории US-2.1–US-2.9 и комплексный сценарий.
 */
class TaskManagementIT : FreeSpec({

    lateinit var taskRepository: TaskRepository
    lateinit var todoTaskService: TodoTaskService

    beforeTest {
        taskRepository = InMemoryTaskRepository()
        todoTaskService = TodoTaskService(taskRepository)
    }

    // ========================================================================
    // US-2.1: Создание задачи
    // ========================================================================

    "create task via add command" - {
        runTest {
            taskRepository.findAll().isEmpty() shouldBe true

            val createdTask = todoTaskService.addTask("Купить молоко")

            createdTask.id.shouldNotBeNull()
            createdTask.title shouldBe "Купить молоко"
            createdTask.status shouldBe TaskStatus.OPEN

            val foundTask = taskRepository.findById(createdTask.id)
            foundTask.shouldNotBeNull()
            foundTask.id shouldBe createdTask.id
            foundTask.title shouldBe "Купить молоко"
        }
    }

    // ========================================================================
    // US-2.2: Просмотр списка задач
    // ========================================================================

    "list all tasks" - {
        runTest {
            val task1 = todoTaskService.addTask("Задача 1")
            val task2 = todoTaskService.addTask("Задача 2")
            val task3 = todoTaskService.addTask("Задача 3")

            todoTaskService.openTask(task2.id)
            todoTaskService.closeTask(null)

            todoTaskService.cancelTask(task3.id)

            val allTasks = todoTaskService.listTasks()

            allTasks.size shouldBe 3
            (allTasks.any { it.id == task1.id && it.status == TaskStatus.OPEN }) shouldBe true
            (allTasks.any { it.id == task2.id && it.status == TaskStatus.CLOSED }) shouldBe true
            (allTasks.any { it.id == task3.id && it.status == TaskStatus.CANCELLED }) shouldBe true
        }
    }

    // ========================================================================
    // US-2.3: Редактирование задачи
    // ========================================================================

    "edit task by id" - {
        runTest {
            val createdTask = todoTaskService.addTask("Старое название")
            val taskId = createdTask.id

            val updatedTask = todoTaskService.editTask(taskId, "Новое название")

            updatedTask.title shouldBe "Новое название"
            updatedTask.id shouldBe taskId

            val foundTask = taskRepository.findById(taskId)
            foundTask.shouldNotBeNull()
            foundTask.title shouldBe "Новое название"
        }
    }

    // ========================================================================
    // US-2.4: Удаление задачи
    // ========================================================================

    "drop task by id" - {
        runTest {
            val createdTask = todoTaskService.addTask("Задача для удаления")
            val taskId = createdTask.id

            taskRepository.exists(taskId) shouldBe true

            todoTaskService.dropTask(taskId)

            taskRepository.findById(taskId) shouldBe null
            taskRepository.exists(taskId) shouldBe false
        }
    }

    // ========================================================================
    // US-2.5: Открытие задачи
    // ========================================================================

    "open task sets currentTaskId" - {
        runTest {
            val createdTask = todoTaskService.addTask("Задача для открытия")
            val taskId = createdTask.id

            todoTaskService.currentTaskId shouldBe null

            val openedTask = todoTaskService.openTask(taskId)

            todoTaskService.currentTaskId shouldBe taskId
            openedTask.id shouldBe taskId
        }
    }

    // ========================================================================
    // US-2.6: Закрытие задачи
    // ========================================================================

    "close task changes status to CLOSED" - {
        runTest {
            val createdTask = todoTaskService.addTask("Задача для закрытия")
            val taskId = createdTask.id
            todoTaskService.openTask(taskId)

            createdTask.status shouldBe TaskStatus.OPEN
            todoTaskService.currentTaskId shouldBe taskId

            val closedTask = todoTaskService.closeTask(null)

            closedTask.status shouldBe TaskStatus.CLOSED
            todoTaskService.currentTaskId shouldBe null

            val foundTask = taskRepository.findById(taskId)
            foundTask.shouldNotBeNull()
            foundTask.status shouldBe TaskStatus.CLOSED
        }
    }

    // ========================================================================
    // US-2.7: Отмена задачи
    // ========================================================================

    "cancel task changes status to CANCELLED" - {
        runTest {
            val createdTask = todoTaskService.addTask("Задача для отмены")
            val taskId = createdTask.id

            createdTask.status shouldBe TaskStatus.OPEN

            val cancelledTask = todoTaskService.cancelTask(taskId)

            cancelledTask.status shouldBe TaskStatus.CANCELLED

            val foundTask = taskRepository.findById(taskId)
            foundTask.shouldNotBeNull()
            foundTask.status shouldBe TaskStatus.CANCELLED
        }
    }

    // ========================================================================
    // US-2.8: Возврат к списку
    // ========================================================================

    "back command clears currentTaskId" - {
        runTest {
            val createdTask = todoTaskService.addTask("Задача для возврата")
            val taskId = createdTask.id
            todoTaskService.openTask(taskId)

            todoTaskService.currentTaskId shouldBe taskId

            todoTaskService.back()

            todoTaskService.currentTaskId shouldBe null
        }
    }

    // ========================================================================
    // US-2.9: Контекстные команды
    // ========================================================================

    "contextual edit without id uses currentTaskId" - {
        runTest {
            val createdTask = todoTaskService.addTask("Старое название")
            val taskId = createdTask.id
            todoTaskService.openTask(taskId)

            todoTaskService.currentTaskId shouldBe taskId

            val updatedTask = todoTaskService.editTask(null, "Новое название")

            updatedTask.title shouldBe "Новое название"
            updatedTask.id shouldBe taskId

            val foundTask = taskRepository.findById(taskId)
            foundTask.shouldNotBeNull()
            foundTask.title shouldBe "Новое название"
        }
    }

    "contextual close without id uses currentTaskId" - {
        runTest {
            val createdTask = todoTaskService.addTask("Задача для контекстного закрытия")
            val taskId = createdTask.id
            todoTaskService.openTask(taskId)

            todoTaskService.currentTaskId shouldBe taskId
            createdTask.status shouldBe TaskStatus.OPEN

            val closedTask = todoTaskService.closeTask(null)

            closedTask.status shouldBe TaskStatus.CLOSED
            closedTask.id shouldBe taskId
            todoTaskService.currentTaskId shouldBe null

            val foundTask = taskRepository.findById(taskId)
            foundTask.shouldNotBeNull()
            foundTask.status shouldBe TaskStatus.CLOSED
        }
    }

    // ========================================================================
    // Комплексный сценарий: Full workflow
    // ========================================================================

    "full workflow - create, open, edit, close, list" - {
        runTest {
            val createdTask = todoTaskService.addTask("Задача 1")
            val taskId = createdTask.id
            createdTask.status shouldBe TaskStatus.OPEN

            val openedTask = todoTaskService.openTask(taskId)
            todoTaskService.currentTaskId shouldBe taskId
            openedTask.id shouldBe taskId

            val updatedTask = todoTaskService.editTask(null, "Обновленное название")
            updatedTask.title shouldBe "Обновленное название"
            updatedTask.id shouldBe taskId

            val closedTask = todoTaskService.closeTask(null)
            closedTask.status shouldBe TaskStatus.CLOSED
            closedTask.id shouldBe taskId
            todoTaskService.currentTaskId shouldBe null

            val allTasks = todoTaskService.listTasks()
            allTasks.size shouldBe 1

            val finalTask = allTasks.first()
            finalTask.id shouldBe taskId
            finalTask.title shouldBe "Обновленное название"
            finalTask.status shouldBe TaskStatus.CLOSED
        }
    }
})

// ============================================================================
// In-memory реализация TaskRepository для тестов
// ============================================================================

/**
 * In-memory реализация [TaskRepository] для интеграционного тестирования.
 *
 * Хранит задачи в [MutableMap], что позволяет быстро тестировать бизнес-логику
 * без необходимости поднимать реальную базу данных.
 */
class InMemoryTaskRepository : TaskRepository {
    private val tasks = mutableMapOf<TaskId, Task>()

    override suspend fun save(task: Task) {
        tasks[task.id] = task
    }

    override suspend fun findById(id: TaskId): Task? {
        return tasks[id]
    }

    override suspend fun findAll(): List<Task> {
        return tasks.values.toList()
    }

    override suspend fun delete(id: TaskId) {
        tasks.remove(id)
    }

    override suspend fun exists(id: TaskId): Boolean {
        return tasks.containsKey(id)
    }

    override suspend fun saveSteps(
        taskId: TaskId,
        steps: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep>
    ) {
        // no-op for task management tests
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep> {
        return emptyList()
    }

    override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
        Result.success(Unit)
}
