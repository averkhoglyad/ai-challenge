package io.averkhogliad.ai.challenge.week3.cli.unit.application.service

import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.*

/**
 * Тесты для [TodoTaskService].
 *
 * Покрывают:
 * - Создание задач (addTask)
 * - Получение списка задач (listTasks)
 * - Редактирование задач (editTask) с явным ID и контекстным
 * - Удаление задач (dropTask) с явным ID и контекстным
 * - Открытие задач (openTask) и установку currentTaskId
 * - Закрытие задач (closeTask) с явным ID и контекстным
 * - Отмену задач (cancelTask) с явным ID и контекстным
 * - Возврат к списку (handleBack)
 * - Обработку ошибок
 */
class TodoTaskServiceTest : FreeSpec({
    /**
     * In-memory реализация TaskRepository для тестирования.
     */
    class InMemoryTaskRepository : TaskRepository {
        private val tasks = mutableMapOf<String, Task>()

        override suspend fun save(task: Task) {
            tasks[task.id.value] = task
        }

        override suspend fun findById(id: TaskId): Task? {
            return tasks[id.value]
        }

        override suspend fun findAll(): List<Task> {
            return tasks.values.toList()
        }

        override suspend fun delete(id: TaskId) {
            tasks.remove(id.value)
        }

        override suspend fun exists(id: TaskId): Boolean {
            return tasks.containsKey(id.value)
        }

        override suspend fun saveSteps(
            taskId: TaskId,
            steps: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep>
        ) {
            // No-op for tests
        }

        override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep> {
            return emptyList()
        }

        override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
            Result.success(Unit)

        override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
            Result.success(Unit)
    }

    lateinit var repository: InMemoryTaskRepository
    lateinit var executor: TodoTaskService

    beforeEach {
        repository = InMemoryTaskRepository()
        executor = TodoTaskService(repository)
    }

    // ==================== addTask ====================

    "addTask should create and save task" {
        runTest {
            val task = executor.addTask("Test Task")

            task.shouldNotBeNull()
            task.title shouldBe "Test Task"
            task.status shouldBe TaskStatus.OPEN
            task.id.shouldNotBeNull()

            // Проверяем, что задача сохранена в репозитории
            val saved = repository.findById(task.id)
            saved.shouldNotBeNull()
            saved.id shouldBe task.id
            saved.title shouldBe task.title
        }
    }

    "addTask should generate unique IDs" {
        runTest {
            val task1 = executor.addTask("Task 1")
            val task2 = executor.addTask("Task 2")

            (task1.id != task2.id) shouldBe true
        }
    }

    // ==================== listTasks ====================

    "listTasks should return all tasks" {
        runTest {
            executor.addTask("Task 1")
            executor.addTask("Task 2")
            executor.addTask("Task 3")

            val tasks = executor.listTasks()

            tasks.size shouldBe 3
        }
    }

    "listTasks should return empty list when no tasks" {
        runTest {
            val tasks = executor.listTasks()

            tasks.isEmpty() shouldBe true
        }
    }

    // ==================== editTask ====================

    "editTask should update task title with explicit ID" {
        runTest {
            val task = executor.addTask("Original Title")

            val updated = executor.editTask(task.id, "Updated Title")

            updated.title shouldBe "Updated Title"
            updated.id shouldBe task.id

            // Проверяем, что обновление сохранено
            val saved = repository.findById(task.id)
            saved.shouldNotBeNull()
            saved.title shouldBe "Updated Title"
        }
    }

    "editTask should use currentTaskId when ID is null" {
        runTest {
            val task = executor.addTask("Original Title")
            executor.openTask(task.id)

            val updated = executor.editTask(null, "Updated Title")

            updated.title shouldBe "Updated Title"
            updated.id shouldBe task.id
        }
    }

    "editTask should throw IllegalStateException when no ID and no current task" {
        runTest {
            shouldThrow<IllegalStateException> {
                executor.editTask(null, "Updated Title")
            }
        }
    }

    "editTask should throw IllegalArgumentException when task not found" {
        runTest {
            val nonExistentId = TaskId("non-existent")

            shouldThrow<IllegalArgumentException> {
                executor.editTask(nonExistentId, "Updated Title")
            }
        }
    }

    // ==================== dropTask ====================

    "dropTask should delete task with explicit ID" {
        runTest {
            val task = executor.addTask("Task to delete")

            executor.dropTask(task.id)

            val deleted = repository.findById(task.id)
            deleted.shouldBeNull()
        }
    }

    "dropTask should use currentTaskId when ID is null" {
        runTest {
            val task = executor.addTask("Task to delete")
            executor.openTask(task.id)

            executor.dropTask(null)

            val deleted = repository.findById(task.id)
            deleted.shouldBeNull()
            executor.currentTaskId.shouldBeNull()
        }
    }

    "dropTask should clear currentTaskId if deleting current task" {
        runTest {
            val task = executor.addTask("Task to delete")
            executor.openTask(task.id)

            executor.currentTaskId shouldBe task.id

            executor.dropTask(task.id)

            executor.currentTaskId.shouldBeNull()
        }
    }

    "dropTask should throw IllegalStateException when no ID and no current task" {
        runTest {
            shouldThrow<IllegalStateException> {
                executor.dropTask(null)
            }
        }
    }

    // ==================== openTask ====================

    "openTask should set currentTaskId" {
        runTest {
            val task = executor.addTask("Task to open")

            val opened = executor.openTask(task.id)

            executor.currentTaskId shouldBe task.id
            opened.id shouldBe task.id
        }
    }

    "openTask should throw IllegalArgumentException when task not found" {
        runTest {
            val nonExistentId = TaskId("non-existent")

            shouldThrow<IllegalArgumentException> {
                executor.openTask(nonExistentId)
            }
        }
    }

    // ==================== closeTask ====================

    "closeTask should close task with explicit ID" {
        runTest {
            val task = executor.addTask("Task to close")

            val closed = executor.closeTask(task.id)

            closed.status shouldBe TaskStatus.CLOSED
            closed.id shouldBe task.id

            // Проверяем, что обновление сохранено
            val saved = repository.findById(task.id)
            saved.shouldNotBeNull()
            saved.status shouldBe TaskStatus.CLOSED
        }
    }

    "closeTask should use currentTaskId when ID is null" {
        runTest {
            val task = executor.addTask("Task to close")
            executor.openTask(task.id)

            val closed = executor.closeTask(null)

            closed.status shouldBe TaskStatus.CLOSED
            closed.id shouldBe task.id
        }
    }

    "closeTask should clear currentTaskId when closing current task" {
        runTest {
            val task = executor.addTask("Task to close")
            executor.openTask(task.id)

            executor.currentTaskId shouldBe task.id

            executor.closeTask(task.id)

            executor.currentTaskId.shouldBeNull()
        }
    }

    "closeTask should throw IllegalStateException when no ID and no current task" {
        runTest {
            shouldThrow<IllegalStateException> {
                executor.closeTask(null)
            }
        }
    }

    "closeTask should throw IllegalArgumentException when task not found" {
        runTest {
            val nonExistentId = TaskId("non-existent")

            shouldThrow<IllegalArgumentException> {
                executor.closeTask(nonExistentId)
            }
        }
    }

    // ==================== cancelTask ====================

    "cancelTask should cancel task with explicit ID" {
        runTest {
            val task = executor.addTask("Task to cancel")

            val cancelled = executor.cancelTask(task.id)

            cancelled.status shouldBe TaskStatus.CANCELLED
            cancelled.id shouldBe task.id

            // Проверяем, что обновление сохранено
            val saved = repository.findById(task.id)
            saved.shouldNotBeNull()
            saved.status shouldBe TaskStatus.CANCELLED
        }
    }

    "cancelTask should use currentTaskId when ID is null" {
        runTest {
            val task = executor.addTask("Task to cancel")
            executor.openTask(task.id)

            val cancelled = executor.cancelTask(null)

            cancelled.status shouldBe TaskStatus.CANCELLED
            cancelled.id shouldBe task.id
        }
    }

    "cancelTask should clear currentTaskId when cancelling current task" {
        runTest {
            val task = executor.addTask("Task to cancel")
            executor.openTask(task.id)

            executor.currentTaskId shouldBe task.id

            executor.cancelTask(task.id)

            executor.currentTaskId.shouldBeNull()
        }
    }

    "cancelTask should throw IllegalStateException when no ID and no current task" {
        runTest {
            shouldThrow<IllegalStateException> {
                executor.cancelTask(null)
            }
        }
    }

    "cancelTask should throw IllegalArgumentException when task not found" {
        runTest {
            val nonExistentId = TaskId("non-existent")

            shouldThrow<IllegalArgumentException> {
                executor.cancelTask(nonExistentId)
            }
        }
    }

    // ==================== handleBack ====================

    "handleBack should clear currentTaskId" {
        runTest {
            val task = executor.addTask("Task")
            executor.openTask(task.id)

            executor.currentTaskId shouldBe task.id

            executor.back()

            executor.currentTaskId.shouldBeNull()
        }
    }

    "handleBack should work when no current task" {
        // Не должно выбрасывать исключение
        executor.back()
        executor.currentTaskId.shouldBeNull()
    }
})
