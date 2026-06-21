package io.averkhogliad.ai.challenge.week2.domain.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тесты для доменной модели [TaskStep] и value object [TaskStepId].
 */
@DisplayName("TaskStep")
class TaskStepTest {

    @Test
    @DisplayName("should create TaskStep with valid data")
    fun `should create with valid data`() {
        val stepId = TaskStepId("step-1")
        val taskId = TaskId("task-1")
        val now = Instant.now()

        val step = TaskStep(
            id = stepId,
            taskId = taskId,
            text = "Implement login",
            isCompleted = false,
            order = 0,
            createdAt = now
        )

        assertEquals(stepId, step.id)
        assertEquals(taskId, step.taskId)
        assertEquals("Implement login", step.text)
        assertFalse(step.isCompleted)
        assertEquals(0, step.order)
        assertEquals(now, step.createdAt)
    }

    @Test
    @DisplayName("should throw when TaskStepId value is blank")
    fun `should throw when TaskStepId is blank`() {
        assertThrows<IllegalArgumentException> {
            TaskStepId("")
        }
    }

    @Test
    @DisplayName("should throw when TaskStepId value is whitespace only")
    fun `should throw when TaskStepId is whitespace`() {
        assertThrows<IllegalArgumentException> {
            TaskStepId("   ")
        }
    }

    @Test
    @DisplayName("should throw when text is blank")
    fun `should throw when text is blank`() {
        val stepId = TaskStepId("step-1")
        val taskId = TaskId("task-1")

        assertThrows<IllegalArgumentException> {
            TaskStep(
                id = stepId,
                taskId = taskId,
                text = "",
                isCompleted = false,
                order = 0,
                createdAt = Instant.now()
            )
        }
    }

    @Test
    @DisplayName("should throw when text is whitespace only")
    fun `should throw when text is whitespace`() {
        val stepId = TaskStepId("step-1")
        val taskId = TaskId("task-1")

        assertThrows<IllegalArgumentException> {
            TaskStep(
                id = stepId,
                taskId = taskId,
                text = "   ",
                isCompleted = false,
                order = 0,
                createdAt = Instant.now()
            )
        }
    }

    @Test
    @DisplayName("should throw when order is negative")
    fun `should throw when order is negative`() {
        val stepId = TaskStepId("step-1")
        val taskId = TaskId("task-1")

        assertThrows<IllegalArgumentException> {
            TaskStep(
                id = stepId,
                taskId = taskId,
                text = "Valid text",
                isCompleted = false,
                order = -1,
                createdAt = Instant.now()
            )
        }
    }

    @Test
    @DisplayName("should mark as completed")
    fun `should mark completed`() {
        val step = createTestStep(isCompleted = false)

        val completedStep = step.markCompleted()

        assertTrue(completedStep.isCompleted)
        // Исходный шаг не изменился (иммутабельность)
        assertFalse(step.isCompleted)
    }

    @Test
    @DisplayName("should mark as incomplete")
    fun `should mark incomplete`() {
        val step = createTestStep(isCompleted = true)

        val incompleteStep = step.markIncomplete()

        assertFalse(incompleteStep.isCompleted)
        // Исходный шаг не изменился (иммутабельность)
        assertTrue(step.isCompleted)
    }

    @Test
    @DisplayName("should update text with valid new text")
    fun `should update text`() {
        val step = createTestStep(text = "Old text")

        val updatedStep = step.updateText("New text")

        assertEquals("New text", updatedStep.text)
        // Исходный шаг не изменился (иммутабельность)
        assertEquals("Old text", step.text)
    }

    @Test
    @DisplayName("should throw when updating text with blank value")
    fun `should throw when updating text with blank`() {
        val step = createTestStep()

        assertThrows<IllegalArgumentException> {
            step.updateText("")
        }
    }

    @Test
    @DisplayName("should throw when updating text with whitespace only")
    fun `should throw when updating text with whitespace`() {
        val step = createTestStep()

        assertThrows<IllegalArgumentException> {
            step.updateText("   ")
        }
    }

    @Test
    @DisplayName("should preserve all other fields after markCompleted")
    fun `should preserve fields after markCompleted`() {
        val step = createTestStep()

        val completedStep = step.markCompleted()

        assertEquals(step.id, completedStep.id)
        assertEquals(step.taskId, completedStep.taskId)
        assertEquals(step.text, completedStep.text)
        assertEquals(step.order, completedStep.order)
        assertEquals(step.createdAt, completedStep.createdAt)
    }

    @Test
    @DisplayName("should preserve all other fields after updateText")
    fun `should preserve fields after updateText`() {
        val step = createTestStep()

        val updatedStep = step.updateText("Changed text")

        assertEquals(step.id, updatedStep.id)
        assertEquals(step.taskId, updatedStep.taskId)
        assertEquals(step.isCompleted, updatedStep.isCompleted)
        assertEquals(step.order, updatedStep.order)
        assertEquals(step.createdAt, updatedStep.createdAt)
    }

    private fun createTestStep(
        stepId: String = "step-1",
        taskId: String = "task-1",
        text: String = "Implement login",
        isCompleted: Boolean = false,
        order: Int = 0
    ): TaskStep = TaskStep(
        id = TaskStepId(stepId),
        taskId = TaskId(taskId),
        text = text,
        isCompleted = isCompleted,
        order = order,
        createdAt = Instant.now()
    )
}
