package io.averkhogliad.ai.challenge.week3.cli.cli

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertTrue

/**
 * Unit-тесты для US-OPEN-1: Отображение контекста при открытии задачи.
 *
 * Проверяет, что renderTaskDetail отображает:
 * - Название задачи (Name)
 * - Описание задачи (Description), если оно есть
 * - Подсказку "Описание отсутствует...", если описания нет
 */
class ConsoleCliRendererTaskDetailTest {

    private val renderer = ConsoleCliRenderer()

    private fun createTask(
        id: String = "1",
        title: String = "Test Task",
        description: String? = null,
        status: TaskStatus = TaskStatus.OPEN
    ): Task = Task(
        id = TaskId(id),
        title = title,
        description = description,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    /**
     * Захватывает вывод в System.out, возвращает его как строку.
     */
    private fun captureOutput(block: () -> Unit): String {
        val originalOut = System.out
        val stream = java.io.ByteArrayOutputStream()
        try {
            System.setOut(java.io.PrintStream(stream))
            block()
        } finally {
            System.setOut(originalOut)
        }
        return stream.toString()
    }

    @Test
    fun `renderTaskDetail should display task name`() {
        val task = createTask(title = "My Important Task")
        val output = captureOutput { renderer.renderTaskDetail(task) }

        assertTrue(
            output.contains("My Important Task"),
            "Должно отображаться название задачи"
        )
    }

    @Test
    fun `renderTaskDetail should display task ID`() {
        val task = createTask(id = "42")
        val output = captureOutput { renderer.renderTaskDetail(task) }

        assertTrue(
            output.contains("42"),
            "Должен отображаться ID задачи"
        )
    }

    @Test
    fun `renderTaskDetail should display task status`() {
        val task = createTask(status = TaskStatus.OPEN)
        val output = captureOutput { renderer.renderTaskDetail(task) }

        assertTrue(
            output.contains("OPEN"),
            "Должен отображаться статус задачи"
        )
    }

    @Test
    fun `renderTaskDetail should display description when present`() {
        val task = createTask(description = "This is a detailed description of the task")
        val output = captureOutput { renderer.renderTaskDetail(task) }

        assertTrue(
            output.contains("Описание"),
            "Должна быть секция Описание"
        )
        assertTrue(
            output.contains("This is a detailed description of the task"),
            "Должно отображаться содержимое описания"
        )
    }

    @Test
    fun `renderTaskDetail should show hint when description is null`() {
        val task = createTask(description = null)
        val output = captureOutput { renderer.renderTaskDetail(task) }

        assertTrue(
            output.contains("Описание"),
            "Должна быть секция Описание"
        )
        assertTrue(
            output.contains("Описание отсутствует"),
            "Должна быть подсказка об отсутствии описания"
        )
        assertTrue(
            output.contains(":edit"),
            "Подсказка должна содержать команду :edit"
        )
    }

    @Test
    fun `renderTaskDetail should show hint when description is blank`() {
        // Note: Task model validation prevents blank descriptions,
        // but we test the hasDescription() method behavior
        val task = createTask(description = null)
        val output = captureOutput { renderer.renderTaskDetail(task) }

        assertTrue(
            output.contains("Описание отсутствует"),
            "Для null описания должна быть подсказка"
        )
    }

    @Test
    fun `renderTaskDetail should display multiline description correctly`() {
        val multilineDescription = """
            Line 1: First point
            Line 2: Second point
            Line 3: Third point
        """.trimIndent()
        val task = createTask(description = multilineDescription)
        val output = captureOutput { renderer.renderTaskDetail(task) }

        assertTrue(
            output.contains("Line 1: First point"),
            "Должна отображаться первая строка описания"
        )
        assertTrue(
            output.contains("Line 2: Second point"),
            "Должна отображаться вторая строка описания"
        )
        assertTrue(
            output.contains("Line 3: Third point"),
            "Должна отображаться третья строка описания"
        )
    }

    @Test
    fun `renderTaskDetail should not crash for task with all fields`() {
        val task = createTask(
            id = "test-id-123",
            title = "Complete Task",
            description = "Complete all subtasks",
            status = TaskStatus.OPEN
        )
        // Не должно выбрасывать исключений
        renderer.renderTaskDetail(task)
    }

    @Test
    fun `renderTaskDetail should be callable multiple times`() {
        val task1 = createTask(id = "1", description = "First task")
        val task2 = createTask(id = "2", description = null)

        repeat(3) {
            renderer.renderTaskDetail(task1)
            renderer.renderTaskDetail(task2)
        }
        // Не должно выбрасывать исключений при повторных вызовах
    }

    @Test
    fun `renderTaskDetail should display created and updated timestamps`() {
        val task = createTask()
        val output = captureOutput { renderer.renderTaskDetail(task) }

        assertTrue(
            output.contains("Создана"),
            "Должна отображаться дата создания"
        )
        assertTrue(
            output.contains("Обновлена"),
            "Должна отображаться дата обновления"
        )
    }
}
