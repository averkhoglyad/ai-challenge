package io.averkhogliad.ai.challenge.week3.cli.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты для доменной модели [Task].
 */
class TaskTest {

    @Test
    fun `should create task with valid data`() {
        val id = TaskId("test-id")
        val title = "Test Task"
        val now = Instant.now()

        val task = Task(
            id = id,
            title = title,
            status = TaskStatus.OPEN,
            createdAt = now,
            updatedAt = now
        )

        assertEquals(id, task.id)
        assertEquals(title, task.title)
        assertEquals(TaskStatus.OPEN, task.status)
        assertEquals(now, task.createdAt)
        assertEquals(now, task.updatedAt)
    }

    @Test
    fun `should throw exception when title is blank`() {
        val id = TaskId("test-id")
        val now = Instant.now()

        assertThrows<IllegalArgumentException> {
            Task(
                id = id,
                title = "",
                status = TaskStatus.OPEN,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    @Test
    fun `should throw exception when TaskId is blank`() {
        assertThrows<IllegalArgumentException> {
            TaskId("")
        }
    }

    @Test
    fun `should check if task is open`() {
        val task = createTask(status = TaskStatus.OPEN)
        assertTrue(task.isOpen())
        assertFalse(task.isClosed())
        assertFalse(task.isCancelled())
    }

    @Test
    fun `should check if task is closed`() {
        val task = createTask(status = TaskStatus.CLOSED)
        assertFalse(task.isOpen())
        assertTrue(task.isClosed())
        assertFalse(task.isCancelled())
    }

    @Test
    fun `should check if task is cancelled`() {
        val task = createTask(status = TaskStatus.CANCELLED)
        assertFalse(task.isOpen())
        assertFalse(task.isClosed())
        assertTrue(task.isCancelled())
    }

    @Test
    fun `should close task`() {
        val task = createTask(status = TaskStatus.OPEN)
        val closedTask = task.close()

        assertEquals(TaskStatus.CLOSED, closedTask.status)
        assertTrue(closedTask.isClosed())
        assertTrue(closedTask.updatedAt >= task.updatedAt)
    }

    @Test
    fun `should cancel task`() {
        val task = createTask(status = TaskStatus.OPEN)
        val cancelledTask = task.cancel()

        assertEquals(TaskStatus.CANCELLED, cancelledTask.status)
        assertTrue(cancelledTask.isCancelled())
        assertTrue(cancelledTask.updatedAt >= task.updatedAt)
    }

    @Test
    fun `should update task title`() {
        val task = createTask(title = "Old Title")
        val newTitle = "New Title"
        val updatedTask = task.updateTitle(newTitle)

        assertEquals(newTitle, updatedTask.title)
        assertTrue(updatedTask.updatedAt >= task.updatedAt)
    }

    @Test
    fun `should throw exception when updating title to blank`() {
        val task = createTask(title = "Test Title")

        assertThrows<IllegalArgumentException> {
            task.updateTitle("")
        }
    }

    @Test
    fun `should create task with description`() {
        val task = createTask(description = "Test description")

        assertEquals("Test description", task.description)
        assertTrue(task.hasDescription())
    }

    @Test
    fun `should create task without description`() {
        val task = createTask()

        assertNull(task.description)
        assertFalse(task.hasDescription())
    }

    @Test
    fun `should throw exception when description is blank`() {
        assertThrows<IllegalArgumentException> {
            createTask(description = "   ")
        }
    }

    @Test
    fun `should update description`() {
        val task = createTask(description = "Old description")
        val updated = task.updateDescription("New description")

        assertEquals("New description", updated.description)
        assertTrue(updated.updatedAt >= task.updatedAt)
    }

    @Test
    fun `should throw exception when updating description to blank`() {
        val task = createTask(description = "Test")

        assertThrows<IllegalArgumentException> {
            task.updateDescription("")
        }
    }

    @Test
    fun `should check hasDescription correctly`() {
        val withDesc = createTask(description = "Test")
        assertTrue(withDesc.hasDescription())

        val withoutDesc = createTask()
        assertFalse(withoutDesc.hasDescription())
    }

    private fun createTask(
        id: String = "test-id",
        title: String = "Test Task",
        description: String? = null,
        status: TaskStatus = TaskStatus.OPEN,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now()
    ): Task = Task(
        id = TaskId(id),
        title = title,
        description = description,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
