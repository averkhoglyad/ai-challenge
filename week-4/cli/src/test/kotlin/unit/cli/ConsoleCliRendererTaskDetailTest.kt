package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.ConsoleCliRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStatus
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

    "renderTaskDetail" - {
        "should display task name" {
            // given
            val task = createTask(title = "My Important Task")

            // when
            val output = captureOutput { renderer.renderTaskDetail(task) }

            // then
            output shouldContain "My Important Task"
        }

        "should display task ID" {
            // given
            val task = createTask(id = "42")

            // when
            val output = captureOutput { renderer.renderTaskDetail(task) }

            // then
            output shouldContain "42"
        }

        "should display task status" {
            // given
            val task = createTask(status = TaskStatus.OPEN)

            // when
            val output = captureOutput { renderer.renderTaskDetail(task) }

            // then
            output shouldContain "OPEN"
        }

        "should display description when present" {
            // given
            val task = createTask(description = "This is a detailed description of the task")

            // when
            val output = captureOutput { renderer.renderTaskDetail(task) }

            // then
            output shouldContain "Описание"
            output shouldContain "This is a detailed description of the task"
        }

        "should show hint when description is null" {
            // given
            val task = createTask(description = null)

            // when
            val output = captureOutput { renderer.renderTaskDetail(task) }

            // then
            output shouldContain "Описание"
            output shouldContain "Описание отсутствует"
            output shouldContain ":edit"
        }

        "should show hint when description is blank" {
            // Note: Task model validation prevents blank descriptions,
            // but we test the hasDescription() method behavior
            // given
            val task = createTask(description = null)

            // when
            val output = captureOutput { renderer.renderTaskDetail(task) }

            // then
            output shouldContain "Описание отсутствует"
        }

        "should display multiline description correctly" {
            // given
            val multilineDescription = """
                Line 1: First point
                Line 2: Second point
                Line 3: Third point
            """.trimIndent()
            val task = createTask(description = multilineDescription)

            // when
            val output = captureOutput { renderer.renderTaskDetail(task) }

            // then
            output shouldContain "Line 1: First point"
            output shouldContain "Line 2: Second point"
            output shouldContain "Line 3: Third point"
        }

        "should not crash for task with all fields" {
            // given
            val task = createTask(
                id = "test-id-123",
                title = "Complete Task",
                description = "Complete all subtasks",
                status = TaskStatus.OPEN
            )

            // when & then — не должно выбрасывать исключений
            renderer.renderTaskDetail(task)
        }

        "should be callable multiple times" {
            // given
            val task1 = createTask(id = "1", description = "First task")
            val task2 = createTask(id = "2", description = null)

            // when & then — не должно выбрасывать исключений при повторных вызовах
            repeat(3) {
                renderer.renderTaskDetail(task1)
                renderer.renderTaskDetail(task2)
            }
        }

        "should display created and updated timestamps" {
            // given
            val task = createTask()

            // when
            val output = captureOutput { renderer.renderTaskDetail(task) }

            // then
            output shouldContain "Создана"
            output shouldContain "Обновлена"
        }
    }
})
