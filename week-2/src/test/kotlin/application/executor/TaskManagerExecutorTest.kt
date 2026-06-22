package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.domain.model.Task
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Тесты для [TaskManagerExecutor].
 *
 * Покрывают:
 * - Создание задач (handleAddTask)
 * - Получение списка задач (handleListTasks)
 * - Редактирование задач (handleEditTask) с явным ID и контекстным
 * - Удаление задач (handleDropTask) с явным ID и контекстным
 * - Открытие задач (handleOpenTask) и установку currentTaskId
 * - Закрытие задач (handleCloseTask) с явным ID и контекстным
 * - Отмену задач (handleCancelTask) с явным ID и контекстным
 * - Возврат к списку (handleBack)
 * - Обработку ошибок
 */
class TaskManagerExecutorTest {

    private lateinit var repository: InMemoryTaskRepository
    private lateinit var executor: TaskManagerExecutor

    @BeforeEach
    fun setUp() {
        repository = InMemoryTaskRepository()
        executor = TaskManagerExecutor(repository)
    }

    // ==================== handleAddTask ====================

    @Test
    fun `handleAddTask should create and save task`() = runBlocking {
        val task = executor.handleAddTask("Test Task")

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
    fun `handleAddTask should generate unique IDs`() = runBlocking {
        val task1 = executor.handleAddTask("Task 1")
        val task2 = executor.handleAddTask("Task 2")

        assertTrue(task1.id != task2.id)
    }

    // ==================== handleListTasks ====================

    @Test
    fun `handleListTasks should return all tasks`() = runBlocking {
        executor.handleAddTask("Task 1")
        executor.handleAddTask("Task 2")
        executor.handleAddTask("Task 3")

        val tasks = executor.handleListTasks()

        assertEquals(3, tasks.size)
    }

    @Test
    fun `handleListTasks should return empty list when no tasks`() = runBlocking {
        val tasks = executor.handleListTasks()

        assertTrue(tasks.isEmpty())
    }

    // ==================== handleEditTask ====================

    @Test
    fun `handleEditTask should update task title with explicit ID`() = runBlocking {
        val task = executor.handleAddTask("Original Title")

        val updated = executor.handleEditTask(task.id, "Updated Title")

        assertEquals("Updated Title", updated.title)
        assertEquals(task.id, updated.id)

        // Проверяем, что обновление сохранено
        val saved = repository.findById(task.id)
        assertNotNull(saved)
        assertEquals("Updated Title", saved.title)
    }

    @Test
    fun `handleEditTask should use currentTaskId when ID is null`() = runBlocking {
        val task = executor.handleAddTask("Original Title")
        executor.handleOpenTask(task.id)

        val updated = executor.handleEditTask(null, "Updated Title")

        assertEquals("Updated Title", updated.title)
        assertEquals(task.id, updated.id)
    }

    @Test
    fun `handleEditTask should throw IllegalStateException when no ID and no current task`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            executor.handleEditTask(null, "Updated Title")
        }
    }

    @Test
    fun `handleEditTask should throw IllegalArgumentException when task not found`() = runBlocking {
        val nonExistentId = TaskId("non-existent")

        assertFailsWith<IllegalArgumentException> {
            executor.handleEditTask(nonExistentId, "Updated Title")
        }
    }

    // ==================== handleDropTask ====================

    @Test
    fun `handleDropTask should delete task with explicit ID`() = runBlocking {
        val task = executor.handleAddTask("Task to delete")

        executor.handleDropTask(task.id)

        val deleted = repository.findById(task.id)
        assertNull(deleted)
    }

    @Test
    fun `handleDropTask should use currentTaskId when ID is null`() = runBlocking {
        val task = executor.handleAddTask("Task to delete")
        executor.handleOpenTask(task.id)

        executor.handleDropTask(null)

        val deleted = repository.findById(task.id)
        assertNull(deleted)
        assertNull(executor.currentTaskId)
    }

    @Test
    fun `handleDropTask should clear currentTaskId if deleting current task`() = runBlocking {
        val task = executor.handleAddTask("Task to delete")
        executor.handleOpenTask(task.id)

        assertEquals(task.id, executor.currentTaskId)

        executor.handleDropTask(task.id)

        assertNull(executor.currentTaskId)
    }

    @Test
    fun `handleDropTask should throw IllegalStateException when no ID and no current task`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            executor.handleDropTask(null)
        }
    }

    // ==================== handleOpenTask ====================

    @Test
    fun `handleOpenTask should set currentTaskId`() = runBlocking {
        val task = executor.handleAddTask("Task to open")

        val opened = executor.handleOpenTask(task.id)

        assertEquals(task.id, executor.currentTaskId)
        assertEquals(task.id, opened.id)
    }

    @Test
    fun `handleOpenTask should throw IllegalArgumentException when task not found`() = runBlocking {
        val nonExistentId = TaskId("non-existent")

        assertFailsWith<IllegalArgumentException> {
            executor.handleOpenTask(nonExistentId)
        }
    }

    // ==================== handleCloseTask ====================

    @Test
    fun `handleCloseTask should close task with explicit ID`() = runBlocking {
        val task = executor.handleAddTask("Task to close")

        val closed = executor.handleCloseTask(task.id)

        assertEquals(TaskStatus.CLOSED, closed.status)
        assertEquals(task.id, closed.id)

        // Проверяем, что обновление сохранено
        val saved = repository.findById(task.id)
        assertNotNull(saved)
        assertEquals(TaskStatus.CLOSED, saved.status)
    }

    @Test
    fun `handleCloseTask should use currentTaskId when ID is null`() = runBlocking {
        val task = executor.handleAddTask("Task to close")
        executor.handleOpenTask(task.id)

        val closed = executor.handleCloseTask(null)

        assertEquals(TaskStatus.CLOSED, closed.status)
        assertEquals(task.id, closed.id)
    }

    @Test
    fun `handleCloseTask should clear currentTaskId when closing current task`() = runBlocking {
        val task = executor.handleAddTask("Task to close")
        executor.handleOpenTask(task.id)

        assertEquals(task.id, executor.currentTaskId)

        executor.handleCloseTask(task.id)

        assertNull(executor.currentTaskId)
    }

    @Test
    fun `handleCloseTask should throw IllegalStateException when no ID and no current task`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            executor.handleCloseTask(null)
        }
    }

    @Test
    fun `handleCloseTask should throw IllegalArgumentException when task not found`() = runBlocking {
        val nonExistentId = TaskId("non-existent")

        assertFailsWith<IllegalArgumentException> {
            executor.handleCloseTask(nonExistentId)
        }
    }

    // ==================== handleCancelTask ====================

    @Test
    fun `handleCancelTask should cancel task with explicit ID`() = runBlocking {
        val task = executor.handleAddTask("Task to cancel")

        val cancelled = executor.handleCancelTask(task.id)

        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertEquals(task.id, cancelled.id)

        // Проверяем, что обновление сохранено
        val saved = repository.findById(task.id)
        assertNotNull(saved)
        assertEquals(TaskStatus.CANCELLED, saved.status)
    }

    @Test
    fun `handleCancelTask should use currentTaskId when ID is null`() = runBlocking {
        val task = executor.handleAddTask("Task to cancel")
        executor.handleOpenTask(task.id)

        val cancelled = executor.handleCancelTask(null)

        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertEquals(task.id, cancelled.id)
    }

    @Test
    fun `handleCancelTask should clear currentTaskId when cancelling current task`() = runBlocking {
        val task = executor.handleAddTask("Task to cancel")
        executor.handleOpenTask(task.id)

        assertEquals(task.id, executor.currentTaskId)

        executor.handleCancelTask(task.id)

        assertNull(executor.currentTaskId)
    }

    @Test
    fun `handleCancelTask should throw IllegalStateException when no ID and no current task`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            executor.handleCancelTask(null)
        }
    }

    @Test
    fun `handleCancelTask should throw IllegalArgumentException when task not found`() = runBlocking {
        val nonExistentId = TaskId("non-existent")

        assertFailsWith<IllegalArgumentException> {
            executor.handleCancelTask(nonExistentId)
        }
    }

    // ==================== handleBack ====================

    @Test
    fun `handleBack should clear currentTaskId`() = runBlocking {
        val task = executor.handleAddTask("Task")
        executor.handleOpenTask(task.id)

        assertEquals(task.id, executor.currentTaskId)

        executor.handleBack()

        assertNull(executor.currentTaskId)
    }

    @Test
    fun `handleBack should work when no current task`() {
        // Не должно выбрасывать исключение
        executor.handleBack()
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
