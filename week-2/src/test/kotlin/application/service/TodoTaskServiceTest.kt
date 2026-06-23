package io.averkhogliad.ai.challenge.week2.application.service

import io.averkhogliad.ai.challenge.week2.domain.model.Task
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

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
class TodoTaskServiceTest {

    private lateinit var repository: InMemoryTaskRepository
    private lateinit var executor: TodoTaskService

    @BeforeEach
    fun setUp() {
        repository = InMemoryTaskRepository()
        executor = TodoTaskService(repository)
    }

    // ==================== addTask ====================

    @Test
    fun `addTask should create and save task`() = runBlocking {
        val task = executor.addTask("Test Task")

        assertNotNull(task)
        assertEquals("Test Task", task.title)
        assertEquals(TaskStatus.OPEN, task.status)
        assertNotNull(task.id)

        // Проверяем, что задача сохранена в репозитории
        val saved = repository.findById(task.id)
        assertNotNull(saved)
        assertEquals(task.id, saved.id)
        assertEquals(task.title, saved.title)
    }

    @Test
    fun `addTask should generate unique IDs`() = runBlocking {
        val task1 = executor.addTask("Task 1")
        val task2 = executor.addTask("Task 2")

        assertTrue(task1.id != task2.id)
    }

    // ==================== listTasks ====================

    @Test
    fun `listTasks should return all tasks`() = runBlocking {
        executor.addTask("Task 1")
        executor.addTask("Task 2")
        executor.addTask("Task 3")

        val tasks = executor.listTasks()

        assertEquals(3, tasks.size)
    }

    @Test
    fun `listTasks should return empty list when no tasks`() = runBlocking {
        val tasks = executor.listTasks()

        assertTrue(tasks.isEmpty())
    }

    // ==================== editTask ====================

    @Test
    fun `editTask should update task title with explicit ID`() = runBlocking {
        val task = executor.addTask("Original Title")

        val updated = executor.editTask(task.id, "Updated Title")

        assertEquals("Updated Title", updated.title)
        assertEquals(task.id, updated.id)

        // Проверяем, что обновление сохранено
        val saved = repository.findById(task.id)
        assertNotNull(saved)
        assertEquals("Updated Title", saved.title)
    }

    @Test
    fun `editTask should use currentTaskId when ID is null`() = runBlocking {
        val task = executor.addTask("Original Title")
        executor.openTask(task.id)

        val updated = executor.editTask(null, "Updated Title")

        assertEquals("Updated Title", updated.title)
        assertEquals(task.id, updated.id)
    }

    @Test
    fun `editTask should throw IllegalStateException when no ID and no current task`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            executor.editTask(null, "Updated Title")
        }
    }

    @Test
    fun `editTask should throw IllegalArgumentException when task not found`() = runBlocking {
        val nonExistentId = TaskId("non-existent")

        assertFailsWith<IllegalArgumentException> {
            executor.editTask(nonExistentId, "Updated Title")
        }
    }

    // ==================== dropTask ====================

    @Test
    fun `dropTask should delete task with explicit ID`() = runBlocking {
        val task = executor.addTask("Task to delete")

        executor.dropTask(task.id)

        val deleted = repository.findById(task.id)
        assertNull(deleted)
    }

    @Test
    fun `dropTask should use currentTaskId when ID is null`() = runBlocking {
        val task = executor.addTask("Task to delete")
        executor.openTask(task.id)

        executor.dropTask(null)

        val deleted = repository.findById(task.id)
        assertNull(deleted)
        assertNull(executor.currentTaskId)
    }

    @Test
    fun `dropTask should clear currentTaskId if deleting current task`() = runBlocking {
        val task = executor.addTask("Task to delete")
        executor.openTask(task.id)

        assertEquals(task.id, executor.currentTaskId)

        executor.dropTask(task.id)

        assertNull(executor.currentTaskId)
    }

    @Test
    fun `dropTask should throw IllegalStateException when no ID and no current task`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            executor.dropTask(null)
        }
    }

    // ==================== openTask ====================

    @Test
    fun `openTask should set currentTaskId`() = runBlocking {
        val task = executor.addTask("Task to open")

        val opened = executor.openTask(task.id)

        assertEquals(task.id, executor.currentTaskId)
        assertEquals(task.id, opened.id)
    }

    @Test
    fun `openTask should throw IllegalArgumentException when task not found`() = runBlocking {
        val nonExistentId = TaskId("non-existent")

        assertFailsWith<IllegalArgumentException> {
            executor.openTask(nonExistentId)
        }
    }

    // ==================== closeTask ====================

    @Test
    fun `closeTask should close task with explicit ID`() = runBlocking {
        val task = executor.addTask("Task to close")

        val closed = executor.closeTask(task.id)

        assertEquals(TaskStatus.CLOSED, closed.status)
        assertEquals(task.id, closed.id)

        // Проверяем, что обновление сохранено
        val saved = repository.findById(task.id)
        assertNotNull(saved)
        assertEquals(TaskStatus.CLOSED, saved.status)
    }

    @Test
    fun `closeTask should use currentTaskId when ID is null`() = runBlocking {
        val task = executor.addTask("Task to close")
        executor.openTask(task.id)

        val closed = executor.closeTask(null)

        assertEquals(TaskStatus.CLOSED, closed.status)
        assertEquals(task.id, closed.id)
    }

    @Test
    fun `closeTask should clear currentTaskId when closing current task`() = runBlocking {
        val task = executor.addTask("Task to close")
        executor.openTask(task.id)

        assertEquals(task.id, executor.currentTaskId)

        executor.closeTask(task.id)

        assertNull(executor.currentTaskId)
    }

    @Test
    fun `closeTask should throw IllegalStateException when no ID and no current task`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            executor.closeTask(null)
        }
    }

    @Test
    fun `closeTask should throw IllegalArgumentException when task not found`() = runBlocking {
        val nonExistentId = TaskId("non-existent")

        assertFailsWith<IllegalArgumentException> {
            executor.closeTask(nonExistentId)
        }
    }

    // ==================== cancelTask ====================

    @Test
    fun `cancelTask should cancel task with explicit ID`() = runBlocking {
        val task = executor.addTask("Task to cancel")

        val cancelled = executor.cancelTask(task.id)

        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertEquals(task.id, cancelled.id)

        // Проверяем, что обновление сохранено
        val saved = repository.findById(task.id)
        assertNotNull(saved)
        assertEquals(TaskStatus.CANCELLED, saved.status)
    }

    @Test
    fun `cancelTask should use currentTaskId when ID is null`() = runBlocking {
        val task = executor.addTask("Task to cancel")
        executor.openTask(task.id)

        val cancelled = executor.cancelTask(null)

        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertEquals(task.id, cancelled.id)
    }

    @Test
    fun `cancelTask should clear currentTaskId when cancelling current task`() = runBlocking {
        val task = executor.addTask("Task to cancel")
        executor.openTask(task.id)

        assertEquals(task.id, executor.currentTaskId)

        executor.cancelTask(task.id)

        assertNull(executor.currentTaskId)
    }

    @Test
    fun `cancelTask should throw IllegalStateException when no ID and no current task`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            executor.cancelTask(null)
        }
    }

    @Test
    fun `cancelTask should throw IllegalArgumentException when task not found`() = runBlocking {
        val nonExistentId = TaskId("non-existent")

        assertFailsWith<IllegalArgumentException> {
            executor.cancelTask(nonExistentId)
        }
    }

    // ==================== handleBack ====================

    @Test
    fun `handleBack should clear currentTaskId`() = runBlocking {
        val task = executor.addTask("Task")
        executor.openTask(task.id)

        assertEquals(task.id, executor.currentTaskId)

        executor.back()

        assertNull(executor.currentTaskId)
    }

    @Test
    fun `handleBack should work when no current task`() {
        // Не должно выбрасывать исключение
        executor.back()
        assertNull(executor.currentTaskId)
    }

    // ==================== InMemoryTaskRepository ====================

    /**
     * In-memory реализация TaskRepository для тестирования.
     */
    private class InMemoryTaskRepository : TaskRepository {
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
            steps: List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep>
        ) {
            // No-op for tests
        }

        override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep> {
            return emptyList()
        }
    }
}
