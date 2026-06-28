package io.averkhogliad.ai.challenge.week3.cli.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты для модели состояния команды [CommandState].
 */
class CommandStateTest {

    @Test
    fun `should create CommandState with valid data`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = "Check open task"
        )

        assertEquals("plan", state.commandName)
        assertEquals(CommandStage.PLANNING, state.currentStage)
        assertEquals(1, state.currentStep)
        assertEquals("Check open task", state.expectedAction)
        assertTrue(state.context.isEmpty())
    }

    @Test
    fun `should throw exception when commandName is blank`() {
        assertThrows<IllegalArgumentException> {
            CommandState(
                commandName = "",
                currentStage = CommandStage.PLANNING
            )
        }
    }

    @Test
    fun `should throw exception when currentStep is less than 1`() {
        assertThrows<IllegalArgumentException> {
            CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 0
            )
        }
    }

    @Test
    fun `should advance to next stage`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 3,
            expectedAction = "Old action"
        )

        val advanced = state.advanceToStage(CommandStage.EXECUTION, "Send LLM request")

        assertEquals(CommandStage.EXECUTION, advanced.currentStage)
        assertEquals(1, advanced.currentStep)
        assertEquals("Send LLM request", advanced.expectedAction)
    }

    @Test
    fun `should advance step within stage`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.EXECUTION,
            currentStep = 1,
            expectedAction = "Step 1"
        )

        val advanced = state.advanceStep("Step 2")

        assertEquals(CommandStage.EXECUTION, advanced.currentStage)
        assertEquals(2, advanced.currentStep)
        assertEquals("Step 2", advanced.expectedAction)
    }

    @Test
    fun `should put and get context values`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING
        )

        val withContext = state
            .putContext("taskId", "123")
            .putContext("description", "Test description")

        assertEquals("123", withContext.getContext("taskId"))
        assertEquals("Test description", withContext.getContext("description"))
    }

    @Test
    fun `should return null for missing context key`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING
        )

        assertNull(state.getContext("nonexistent"))
    }

    @Test
    fun `should check if command is done`() {
        val planningState = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING
        )
        assertFalse(planningState.isDone())

        val doneState = CommandState(
            commandName = "plan",
            currentStage = CommandStage.DONE
        )
        assertTrue(doneState.isDone())
    }

    @Test
    fun `should preserve context when advancing stage`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING
        ).putContext("key", "value")

        val advanced = state.advanceToStage(CommandStage.EXECUTION)

        assertEquals("value", advanced.getContext("key"))
    }

    @Test
    fun `should preserve context when advancing step`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.EXECUTION,
            currentStep = 1
        ).putContext("key", "value")

        val advanced = state.advanceStep()

        assertEquals("value", advanced.getContext("key"))
    }
}
