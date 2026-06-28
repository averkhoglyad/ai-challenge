package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Тесты для контракта интерфейса [TaskRepository].
 * Использует in-memory реализацию для проверки контракта.
 */
class TaskRepositoryTest {

    private lateinit var repository: TaskRepository

    @BeforeEach
    fun setUp() {
        repository = InMemoryTaskRepository()
    }

    @Test
    fun `should save and find task by id`() = runBlocking {
        val task = createTask("task-1", "Test Task")
        repository.save(task)

        val found = repository.findById(TaskId("task-1"))
        assertNotNull(found)
        assertEquals(task.id, found.id)
        assertEquals(task.title, found.title)
        assertEquals(task.status, found.status)
    }

    @Test
    fun `should return null when task not found`() = runBlocking {
        val found = repository.findById(TaskId("non-existent"))
        assertNull(found)
    }

    @Test
    fun `should find all tasks`() = runBlocking {
        val task1 = createTask("task-1", "Task 1")
        val task2 = createTask("task-2", "Task 2")
        val task3 = createTask("task-3", "Task 3")

        repository.save(task1)
        repository.save(task2)
        repository.save(task3)

        val all = repository.findAll()
        assertEquals(3, all.size)
    }

    @Test
    fun `should delete task`() = runBlocking {
        val task = createTask("task-1", "Test Task")
        repository.save(task)

        assertTrue(repository.exists(TaskId("task-1")))

        repository.delete(TaskId("task-1"))

        assertFalse(repository.exists(TaskId("task-1")))
        assertNull(repository.findById(TaskId("task-1")))
    }

    @Test
    fun `should check if task exists`() = runBlocking {
        val task = createTask("task-1", "Test Task")
        repository.save(task)

        assertTrue(repository.exists(TaskId("task-1")))
        assertFalse(repository.exists(TaskId("non-existent")))
    }

    @Test
    fun `should update task on save`() = runBlocking {
        val task = createTask("task-1", "Original Title")
        repository.save(task)

        val updatedTask = task.updateTitle("Updated Title")
        repository.save(updatedTask)

        val found = repository.findById(TaskId("task-1"))
        assertNotNull(found)
        assertEquals("Updated Title", found.title)
    }

    @Test
    fun `should handle different task statuses`() = runBlocking {
        val openTask = createTask("open", "Open Task", TaskStatus.OPEN)
        val closedTask = createTask("closed", "Closed Task", TaskStatus.CLOSED)
        val cancelledTask = createTask("cancelled", "Cancelled Task", TaskStatus.CANCELLED)

        repository.save(openTask)
        repository.save(closedTask)
        repository.save(cancelledTask)

        assertEquals(TaskStatus.OPEN, repository.findById(TaskId("open"))?.status)
        assertEquals(TaskStatus.CLOSED, repository.findById(TaskId("closed"))?.status)
        assertEquals(TaskStatus.CANCELLED, repository.findById(TaskId("cancelled"))?.status)
    }

    private fun createTask(
        id: String,
        title: String,
        status: TaskStatus = TaskStatus.OPEN
    ): Task = Task(
        id = TaskId(id),
        title = title,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

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
            steps: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep>
        ) {
            // No-op for tests
        }

        override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep> {
            return emptyList()
        }
    }
}
