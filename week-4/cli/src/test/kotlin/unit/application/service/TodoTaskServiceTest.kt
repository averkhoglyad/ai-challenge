package io.averkhogliad.ai.challenge.week4.cli.unit.application.service

import io.averkhogliad.ai.challenge.week4.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week4.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskRepository
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
            steps: List<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep>
        ) {
            // No-op for tests
        }

        override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep> {
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

    "addTask" - {
        "should create and save task" {
            runTest {
                // when
                val task = executor.addTask("Test Task")

                // then
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

        "should generate unique IDs" {
            runTest {
                // when
                val task1 = executor.addTask("Task 1")
                val task2 = executor.addTask("Task 2")

                // then
                (task1.id != task2.id) shouldBe true
            }
        }
    }

    "listTasks" - {
        "should return all tasks" {
            runTest {
                // given
                executor.addTask("Task 1")
                executor.addTask("Task 2")
                executor.addTask("Task 3")

                // when
                val tasks = executor.listTasks()

                // then
                tasks.size shouldBe 3
            }
        }

        "should return empty list when no tasks" {
            runTest {
                // when
                val tasks = executor.listTasks()

                // then
                tasks.isEmpty() shouldBe true
            }
        }
    }

    "editTask" - {
        "should update task title with explicit ID" {
            runTest {
                // given
                val task = executor.addTask("Original Title")

                // when
                val updated = executor.editTask(task.id, "Updated Title")

                // then
                updated.title shouldBe "Updated Title"
                updated.id shouldBe task.id

                // Проверяем, что обновление сохранено
                val saved = repository.findById(task.id)
                saved.shouldNotBeNull()
                saved.title shouldBe "Updated Title"
            }
        }

        "should use currentTaskId when ID is null" {
            runTest {
                // given
                val task = executor.addTask("Original Title")
                executor.openTask(task.id)

                // when
                val updated = executor.editTask(null, "Updated Title")

                // then
                updated.title shouldBe "Updated Title"
                updated.id shouldBe task.id
            }
        }

        "should throw IllegalStateException when no ID and no current task" {
            runTest {
                // when & then
                shouldThrow<IllegalStateException> {
                    executor.editTask(null, "Updated Title")
                }
            }
        }

        "should throw IllegalArgumentException when task not found" {
            runTest {
                // given
                val nonExistentId = TaskId("non-existent")

                // when & then
                shouldThrow<IllegalArgumentException> {
                    executor.editTask(nonExistentId, "Updated Title")
                }
            }
        }
    }

    "dropTask" - {
        "should delete task with explicit ID" {
            runTest {
                // given
                val task = executor.addTask("Task to delete")

                // when
                executor.dropTask(task.id)

                // then
                val deleted = repository.findById(task.id)
                deleted.shouldBeNull()
            }
        }

        "should use currentTaskId when ID is null" {
            runTest {
                // given
                val task = executor.addTask("Task to delete")
                executor.openTask(task.id)

                // when
                executor.dropTask(null)

                // then
                val deleted = repository.findById(task.id)
                deleted.shouldBeNull()
                executor.currentTaskId.shouldBeNull()
            }
        }

        "should clear currentTaskId if deleting current task" {
            runTest {
                // given
                val task = executor.addTask("Task to delete")
                executor.openTask(task.id)

                executor.currentTaskId shouldBe task.id

                // when
                executor.dropTask(task.id)

                // then
                executor.currentTaskId.shouldBeNull()
            }
        }

        "should throw IllegalStateException when no ID and no current task" {
            runTest {
                // when & then
                shouldThrow<IllegalStateException> {
                    executor.dropTask(null)
                }
            }
        }
    }

    "openTask" - {
        "should set currentTaskId" {
            runTest {
                // given
                val task = executor.addTask("Task to open")

                // when
                val opened = executor.openTask(task.id)

                // then
                executor.currentTaskId shouldBe task.id
                opened.id shouldBe task.id
            }
        }

        "should throw IllegalArgumentException when task not found" {
            runTest {
                // given
                val nonExistentId = TaskId("non-existent")

                // when & then
                shouldThrow<IllegalArgumentException> {
                    executor.openTask(nonExistentId)
                }
            }
        }
    }

    "closeTask" - {
        "should close task with explicit ID" {
            runTest {
                // given
                val task = executor.addTask("Task to close")

                // when
                val closed = executor.closeTask(task.id)

                // then
                closed.status shouldBe TaskStatus.CLOSED
                closed.id shouldBe task.id

                // Проверяем, что обновление сохранено
                val saved = repository.findById(task.id)
                saved.shouldNotBeNull()
                saved.status shouldBe TaskStatus.CLOSED
            }
        }

        "should use currentTaskId when ID is null" {
            runTest {
                // given
                val task = executor.addTask("Task to close")
                executor.openTask(task.id)

                // when
                val closed = executor.closeTask(null)

                // then
                closed.status shouldBe TaskStatus.CLOSED
                closed.id shouldBe task.id
            }
        }

        "should clear currentTaskId when closing current task" {
            runTest {
                // given
                val task = executor.addTask("Task to close")
                executor.openTask(task.id)

                executor.currentTaskId shouldBe task.id

                // when
                executor.closeTask(task.id)

                // then
                executor.currentTaskId.shouldBeNull()
            }
        }

        "should throw IllegalStateException when no ID and no current task" {
            runTest {
                // when & then
                shouldThrow<IllegalStateException> {
                    executor.closeTask(null)
                }
            }
        }

        "should throw IllegalArgumentException when task not found" {
            runTest {
                // given
                val nonExistentId = TaskId("non-existent")

                // when & then
                shouldThrow<IllegalArgumentException> {
                    executor.closeTask(nonExistentId)
                }
            }
        }
    }

    "cancelTask" - {
        "should cancel task with explicit ID" {
            runTest {
                // given
                val task = executor.addTask("Task to cancel")

                // when
                val cancelled = executor.cancelTask(task.id)

                // then
                cancelled.status shouldBe TaskStatus.CANCELLED
                cancelled.id shouldBe task.id

                // Проверяем, что обновление сохранено
                val saved = repository.findById(task.id)
                saved.shouldNotBeNull()
                saved.status shouldBe TaskStatus.CANCELLED
            }
        }

        "should use currentTaskId when ID is null" {
            runTest {
                // given
                val task = executor.addTask("Task to cancel")
                executor.openTask(task.id)

                // when
                val cancelled = executor.cancelTask(null)

                // then
                cancelled.status shouldBe TaskStatus.CANCELLED
                cancelled.id shouldBe task.id
            }
        }

        "should clear currentTaskId when cancelling current task" {
            runTest {
                // given
                val task = executor.addTask("Task to cancel")
                executor.openTask(task.id)

                executor.currentTaskId shouldBe task.id

                // when
                executor.cancelTask(task.id)

                // then
                executor.currentTaskId.shouldBeNull()
            }
        }

        "should throw IllegalStateException when no ID and no current task" {
            runTest {
                // when & then
                shouldThrow<IllegalStateException> {
                    executor.cancelTask(null)
                }
            }
        }

        "should throw IllegalArgumentException when task not found" {
            runTest {
                // given
                val nonExistentId = TaskId("non-existent")

                // when & then
                shouldThrow<IllegalArgumentException> {
                    executor.cancelTask(nonExistentId)
                }
            }
        }
    }

    "handleBack" - {
        "should clear currentTaskId" {
            runTest {
                // given
                val task = executor.addTask("Task")
                executor.openTask(task.id)

                executor.currentTaskId shouldBe task.id

                // when
                executor.back()

                // then
                executor.currentTaskId.shouldBeNull()
            }
        }

        "should work when no current task" {
            // when
            executor.back()

            // then - не должно выбрасывать исключение
            executor.currentTaskId.shouldBeNull()
        }
    }
})
