package io.averkhogliad.ai.challenge.week2.infrastructure.persistence

import io.averkhogliad.ai.challenge.week2.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.*

/**
 * Интеграционные тесты для [SqliteTaskRepository].
 * Использует временный файл для SQLite базы данных.
 */
class SqliteTaskRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: SqliteTaskRepository
    private lateinit var dbPath: String

    @BeforeEach
    fun setUp() {
        dbPath = tempDir.resolve("test-tasks.db").toString()
        repository = SqliteTaskRepository(dbPath)
    }

    @AfterEach
    fun tearDown() {
        repository.close()
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
        assertEquals(task.createdAt, found.createdAt)
        assertEquals(task.updatedAt, found.updatedAt)
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

    @Test
    fun `should persist task with closed status`() = runBlocking {
        val task = createTask("task-1", "Test Task", TaskStatus.CLOSED)
        repository.save(task)

        val found = repository.findById(TaskId("task-1"))
        assertNotNull(found)
        assertEquals(TaskStatus.CLOSED, found.status)
    }

    @Test
    fun `should persist task with cancelled status`() = runBlocking {
        val task = createTask("task-1", "Test Task", TaskStatus.CANCELLED)
        repository.save(task)

        val found = repository.findById(TaskId("task-1"))
        assertNotNull(found)
        assertEquals(TaskStatus.CANCELLED, found.status)
    }

    @Test
    fun `should return empty list when no tasks`() = runBlocking {
        val all = repository.findAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `should handle delete of non-existent task`() = runBlocking {
        // Не должно выбрасывать исключение
        repository.delete(TaskId("non-existent"))
    }

    @Test
    fun `should save and find steps by task id`() = runBlocking {
        val task = createTask("task-1", "Test Task")
        repository.save(task)

        val steps = listOf(
            TaskStep(
                id = TaskStepId("step-1"),
                taskId = TaskId("task-1"),
                text = "First step",
                isCompleted = false,
                order = 1,
                createdAt = Instant.now()
            ),
            TaskStep(
                id = TaskStepId("step-2"),
                taskId = TaskId("task-1"),
                text = "Second step",
                isCompleted = false,
                order = 2,
                createdAt = Instant.now()
            )
        )

        repository.saveSteps(TaskId("task-1"), steps)

        val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
        assertEquals(2, foundSteps.size)
        assertEquals("First step", foundSteps[0].text)
        assertEquals("Second step", foundSteps[1].text)
        assertEquals(1, foundSteps[0].order)
        assertEquals(2, foundSteps[1].order)
    }

    @Test
    fun `should return empty list when no steps for task`() = runBlocking {
        val task = createTask("task-1", "Test Task")
        repository.save(task)

        val steps = repository.findStepsByTaskId(TaskId("task-1"))
        assertTrue(steps.isEmpty())
    }

    @Test
    fun `should replace steps when saving again`() = runBlocking {
        val task = createTask("task-1", "Test Task")
        repository.save(task)

        val initialSteps = listOf(
            TaskStep(
                id = TaskStepId("step-1"),
                taskId = TaskId("task-1"),
                text = "Initial step",
                isCompleted = false,
                order = 1,
                createdAt = Instant.now()
            )
        )
        repository.saveSteps(TaskId("task-1"), initialSteps)

        val newSteps = listOf(
            TaskStep(
                id = TaskStepId("step-2"),
                taskId = TaskId("task-1"),
                text = "New step 1",
                isCompleted = false,
                order = 1,
                createdAt = Instant.now()
            ),
            TaskStep(
                id = TaskStepId("step-3"),
                taskId = TaskId("task-1"),
                text = "New step 2",
                isCompleted = false,
                order = 2,
                createdAt = Instant.now()
            )
        )
        repository.saveSteps(TaskId("task-1"), newSteps)

        val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
        assertEquals(2, foundSteps.size)
        assertEquals("New step 1", foundSteps[0].text)
        assertEquals("New step 2", foundSteps[1].text)
    }

    @Test
    fun `should save steps with completed status`() = runBlocking {
        val task = createTask("task-1", "Test Task")
        repository.save(task)

        val steps = listOf(
            TaskStep(
                id = TaskStepId("step-1"),
                taskId = TaskId("task-1"),
                text = "Completed step",
                isCompleted = true,
                order = 1,
                createdAt = Instant.now()
            )
        )

        repository.saveSteps(TaskId("task-1"), steps)

        val foundSteps = repository.findStepsByTaskId(TaskId("task-1"))
        assertEquals(1, foundSteps.size)
        assertTrue(foundSteps[0].isCompleted)
    }

    private fun createTask(
        id: String,
        title: String,
        status: TaskStatus = TaskStatus.OPEN,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now()
    ): Task = Task(
        id = TaskId(id),
        title = title,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
