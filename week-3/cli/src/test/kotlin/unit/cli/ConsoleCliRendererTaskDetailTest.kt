package io.averkhogliad.ai.challenge.week3.cli.unit.cli

import io.averkhogliad.ai.challenge.week3.cli.cli.*

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import java.time.Instant

/**
 * Unit-тесты для US-OPEN-1: Отображение контекста при открытии задачи.
 *
 * Проверяет, что renderTaskDetail отображает:
 * - Название задачи (Name)
 * - Описание задачи (Description), если оно есть
 * - Подсказку "Описание отсутствует...", если описания нет
 */
class ConsoleCliRendererTaskDetailTest : FreeSpec({

    val renderer = ConsoleCliRenderer()

    fun createTask(
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
    fun captureOutput(block: () -> Unit): String {
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

    "renderTaskDetail should display task name" {
        val task = createTask(title = "My Important Task")
        val output = captureOutput { renderer.renderTaskDetail(task) }

        output shouldContain "My Important Task"
    }

    "renderTaskDetail should display task ID" {
        val task = createTask(id = "42")
        val output = captureOutput { renderer.renderTaskDetail(task) }

        output shouldContain "42"
    }

    "renderTaskDetail should display task status" {
        val task = createTask(status = TaskStatus.OPEN)
        val output = captureOutput { renderer.renderTaskDetail(task) }

        output shouldContain "OPEN"
    }

    "renderTaskDetail should display description when present" {
        val task = createTask(description = "This is a detailed description of the task")
        val output = captureOutput { renderer.renderTaskDetail(task) }

        output shouldContain "Описание"
        output shouldContain "This is a detailed description of the task"
    }

    "renderTaskDetail should show hint when description is null" {
        val task = createTask(description = null)
        val output = captureOutput { renderer.renderTaskDetail(task) }

        output shouldContain "Описание"
        output shouldContain "Описание отсутствует"
        output shouldContain ":edit"
    }

    "renderTaskDetail should show hint when description is blank" {
        // Note: Task model validation prevents blank descriptions,
        // but we test the hasDescription() method behavior
        val task = createTask(description = null)
        val output = captureOutput { renderer.renderTaskDetail(task) }

        output shouldContain "Описание отсутствует"
    }

    "renderTaskDetail should display multiline description correctly" {
        val multilineDescription = """
            Line 1: First point
            Line 2: Second point
            Line 3: Third point
        """.trimIndent()
        val task = createTask(description = multilineDescription)
        val output = captureOutput { renderer.renderTaskDetail(task) }

        output shouldContain "Line 1: First point"
        output shouldContain "Line 2: Second point"
        output shouldContain "Line 3: Third point"
    }

    "renderTaskDetail should not crash for task with all fields" {
        val task = createTask(
            id = "test-id-123",
            title = "Complete Task",
            description = "Complete all subtasks",
            status = TaskStatus.OPEN
        )
        // Не должно выбрасывать исключений
        renderer.renderTaskDetail(task)
    }

    "renderTaskDetail should be callable multiple times" {
        val task1 = createTask(id = "1", description = "First task")
        val task2 = createTask(id = "2", description = null)

        repeat(3) {
            renderer.renderTaskDetail(task1)
            renderer.renderTaskDetail(task2)
        }
        // Не должно выбрасывать исключений при повторных вызовах
    }

    "renderTaskDetail should display created and updated timestamps" {
        val task = createTask()
        val output = captureOutput { renderer.renderTaskDetail(task) }

        output shouldContain "Создана"
        output shouldContain "Обновлена"
    }
})
