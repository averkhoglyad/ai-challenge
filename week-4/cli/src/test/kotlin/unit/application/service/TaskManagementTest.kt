package io.averkhogliad.ai.challenge.week4.cli.unit.application.service

import io.averkhogliad.ai.challenge.week4.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week4.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.*

/**
 * Unit-тесты для проверки сценариев управления задачами.
 *
 * Покрывают пользовательские истории US-2.1–US-2.9 и комплексный сценарий.
 */
class TaskManagementTest : FreeSpec({

    lateinit var taskRepository: TaskRepository
    lateinit var todoTaskService: TodoTaskService

    beforeTest {
        taskRepository = InMemoryTaskRepository()
        todoTaskService = TodoTaskService(taskRepository)
    }

    "US-2.1: create task" - {
        "create task via add command" {
            runTest {
                // given - пустой репозиторий
                taskRepository.findAll().shouldHaveSize(0)

                // when - выполняем :add "Купить молоко"
                val createdTask = todoTaskService.addTask("Купить молоко")

                // then - задача создана, статус OPEN, можно найти по ID
                createdTask.id shouldNotBe null
                createdTask.title shouldBe "Купить молоко"
                createdTask.status shouldBe TaskStatus.OPEN

                val foundTask = taskRepository.findById(createdTask.id)
                foundTask shouldNotBe null
                foundTask!!.id shouldBe createdTask.id
                foundTask.title shouldBe "Купить молоко"
            }
        }
    }

    "US-2.2: list tasks" - {
        "list all tasks" {
            runTest {
                // given - 3 задачи с разными статусами
                val task1 = todoTaskService.addTask("Задача 1")
                val task2 = todoTaskService.addTask("Задача 2")
                val task3 = todoTaskService.addTask("Задача 3")

                // given - закрываем вторую задачу
                todoTaskService.openTask(task2.id)
                todoTaskService.closeTask(null)

                // given - отменяем третью задачу
                todoTaskService.cancelTask(task3.id)

                // when - выполняем :list
                val allTasks = todoTaskService.listTasks()

                // then - все задачи отображаются
                allTasks shouldHaveSize 3
                (allTasks.any { it.id == task1.id && it.status == TaskStatus.OPEN }) shouldBe true
                (allTasks.any { it.id == task2.id && it.status == TaskStatus.CLOSED }) shouldBe true
                (allTasks.any { it.id == task3.id && it.status == TaskStatus.CANCELLED }) shouldBe true
            }
        }
    }

    "US-2.3: edit task" - {
        "edit task by id" {
            runTest {
                // given - задача с ID "abc123"
                val createdTask = todoTaskService.addTask("Старое название")
                val taskId = createdTask.id

                // when - выполняем :edit abc123 "Новое название"
                val updatedTask = todoTaskService.editTask(taskId, "Новое название")

                // then - заголовок задачи обновлен
                updatedTask.title shouldBe "Новое название"
                updatedTask.id shouldBe taskId

                val foundTask = taskRepository.findById(taskId)
                foundTask shouldNotBe null
                foundTask!!.title shouldBe "Новое название"
            }
        }
    }

    "US-2.4: drop task" - {
        "drop task by id" {
            runTest {
                // given - задача с ID "abc123"
                val createdTask = todoTaskService.addTask("Задача для удаления")
                val taskId = createdTask.id

                taskRepository.exists(taskId) shouldBe true

                // when - выполняем :drop abc123
                todoTaskService.dropTask(taskId)

                // then - задача удалена из репозитория
                taskRepository.findById(taskId) shouldBe null
                taskRepository.exists(taskId) shouldBe false
            }
        }
    }

    "US-2.5: open task" - {
        "open task sets currentTaskId" {
            runTest {
                // given - задача с ID "abc123"
                val createdTask = todoTaskService.addTask("Задача для открытия")
                val taskId = createdTask.id

                todoTaskService.currentTaskId shouldBe null

                // when - выполняем :open abc123
                val openedTask = todoTaskService.openTask(taskId)

                // then - currentTaskId установлен в "abc123"
                todoTaskService.currentTaskId shouldBe taskId
                openedTask.id shouldBe taskId
            }
        }
    }

    "US-2.6: close task" - {
        "close task changes status to CLOSED" {
            runTest {
                // given - открытая задача с ID "abc123"
                val createdTask = todoTaskService.addTask("Задача для закрытия")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)

                createdTask.status shouldBe TaskStatus.OPEN
                todoTaskService.currentTaskId shouldBe taskId

                // when - выполняем :close
                val closedTask = todoTaskService.closeTask(null)

                // then - статус задачи изменен на CLOSED, currentTaskId очищен
                closedTask.status shouldBe TaskStatus.CLOSED
                todoTaskService.currentTaskId shouldBe null

                val foundTask = taskRepository.findById(taskId)
                foundTask shouldNotBe null
                foundTask!!.status shouldBe TaskStatus.CLOSED
            }
        }
    }

    "US-2.7: cancel task" - {
        "cancel task changes status to CANCELLED" {
            runTest {
                // given - задача с ID "abc123"
                val createdTask = todoTaskService.addTask("Задача для отмены")
                val taskId = createdTask.id

                createdTask.status shouldBe TaskStatus.OPEN

                // when - выполняем :cancel abc123
                val cancelledTask = todoTaskService.cancelTask(taskId)

                // then - статус задачи изменен на CANCELLED
                cancelledTask.status shouldBe TaskStatus.CANCELLED

                val foundTask = taskRepository.findById(taskId)
                foundTask shouldNotBe null
                foundTask!!.status shouldBe TaskStatus.CANCELLED
            }
        }
    }

    "US-2.8: back to list" - {
        "back command clears currentTaskId" {
            runTest {
                // given - открытая задача
                val createdTask = todoTaskService.addTask("Задача для возврата")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)

                todoTaskService.currentTaskId shouldBe taskId

                // when - выполняем :back
                todoTaskService.back()

                // then - currentTaskId очищен
                todoTaskService.currentTaskId shouldBe null
            }
        }
    }

    "US-2.9: contextual commands" - {
        "contextual edit without id uses currentTaskId" {
            runTest {
                // given - открытая задача с ID "abc123"
                val createdTask = todoTaskService.addTask("Старое название")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)

                todoTaskService.currentTaskId shouldBe taskId

                // when - выполняем :edit "Новое название" (без ID)
                val updatedTask = todoTaskService.editTask(null, "Новое название")

                // then - задача с ID "abc123" обновлена
                updatedTask.title shouldBe "Новое название"
                updatedTask.id shouldBe taskId

                val foundTask = taskRepository.findById(taskId)
                foundTask shouldNotBe null
                foundTask!!.title shouldBe "Новое название"
            }
        }

        "contextual close without id uses currentTaskId" {
            runTest {
                // given - открытая задача с ID "abc123"
                val createdTask = todoTaskService.addTask("Задача для контекстного закрытия")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)

                todoTaskService.currentTaskId shouldBe taskId
                createdTask.status shouldBe TaskStatus.OPEN

                // when - выполняем :close (без ID)
                val closedTask = todoTaskService.closeTask(null)

                // then - задача с ID "abc123" закрыта
                closedTask.status shouldBe TaskStatus.CLOSED
                closedTask.id shouldBe taskId
                todoTaskService.currentTaskId shouldBe null

                val foundTask = taskRepository.findById(taskId)
                foundTask shouldNotBe null
                foundTask!!.status shouldBe TaskStatus.CLOSED
            }
        }
    }

    "full workflow" - {
        "full workflow - create, open, edit, close, list" {
            runTest {
                // given - создаем задачу: :add "Задача 1"
                val createdTask = todoTaskService.addTask("Задача 1")
                val taskId = createdTask.id
                createdTask.status shouldBe TaskStatus.OPEN

                // when - открываем задачу: :open <id>
                val openedTask = todoTaskService.openTask(taskId)
                todoTaskService.currentTaskId shouldBe taskId
                openedTask.id shouldBe taskId

                // when - редактируем контекстно: :edit "Обновленное название"
                val updatedTask = todoTaskService.editTask(null, "Обновленное название")
                updatedTask.title shouldBe "Обновленное название"
                updatedTask.id shouldBe taskId

                // when - закрываем контекстно: :close
                val closedTask = todoTaskService.closeTask(null)
                closedTask.status shouldBe TaskStatus.CLOSED
                closedTask.id shouldBe taskId
                todoTaskService.currentTaskId shouldBe null

                // when - проверяем список: :list
                val allTasks = todoTaskService.listTasks()

                // then
                allTasks shouldHaveSize 1

                // then - задача должна быть в статусе CLOSED
                val finalTask = allTasks.first()
                finalTask.id shouldBe taskId
                finalTask.title shouldBe "Обновленное название"
                finalTask.status shouldBe TaskStatus.CLOSED
            }
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
        steps: List<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep>
    ) {
        // no-op for task management tests
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep> {
        return emptyList()
    }

    override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
        Result.success(Unit)
}
