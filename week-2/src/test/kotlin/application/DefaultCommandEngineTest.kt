package io.averkhogliad.ai.challenge.week2.application

import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit-тесты для DefaultCommandEngine.
 * 
 * Покрывают:
 * - Создание и запуск команд
 * - Переходы между этапами и шагами
 * - Работу с контекстом
 * - Завершение и отмену команд
 * - Обработку ошибок
 */
class DefaultCommandEngineTest {

    private lateinit var engine: DefaultCommandEngine

    @BeforeEach
    fun setUp() {
        engine = DefaultCommandEngine()
    }

    // ========== Тесты создания команды ==========

    @Test
    fun `startCommand creates new command in PLANNING stage`() {
        // When
        engine.startCommand("plan", "Check task")

        // Then
        assertTrue(engine.hasActiveCommand())
        val state = engine.getActiveState()
        assertNotNull(state)
        assertEquals("plan", state!!.commandName)
        assertEquals(CommandStage.PLANNING, state.currentStage)
        assertEquals(1, state.currentStep)
        assertEquals("Check task", state.expectedAction)
    }

    @Test
    fun `startCommand with blank name throws exception`() {
        // When & Then
        assertThrows<IllegalArgumentException> {
            engine.startCommand("", "action")
        }
    }

    @Test
    fun `startCommand when another command active throws exception`() {
        // Given
        engine.startCommand("plan", "action1")

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            engine.startCommand("describe", "action2")
        }
        assertTrue(exception.message!!.contains("another command 'plan' is already active"))
    }

    // ========== Тесты проверки активной команды ==========

    @Test
    fun `hasActiveCommand returns false when no command started`() {
        // Then
        assertFalse(engine.hasActiveCommand())
    }

    @Test
    fun `hasActiveCommand returns true after startCommand`() {
        // Given
        engine.startCommand("plan", "action")

        // Then
        assertTrue(engine.hasActiveCommand())
    }

    @Test
    fun `getActiveState returns null when no command started`() {
        // Then
        assertNull(engine.getActiveState())
    }

    @Test
    fun `getActiveState returns state after startCommand`() {
        // Given
        engine.startCommand("plan", "action")

        // Then
        val state = engine.getActiveState()
        assertNotNull(state)
        assertEquals("plan", state!!.commandName)
    }

    // ========== Тесты перехода между этапами ==========

    @Test
    fun `advanceToStage moves from PLANNING to EXECUTION`() {
        // Given
        engine.startCommand("plan", "Check task")

        // When
        engine.advanceToStage("Generate steps")

        // Then
        val state = engine.getActiveState()
        assertEquals(CommandStage.EXECUTION, state!!.currentStage)
        assertEquals(1, state.currentStep) // Сброс шага на 1
        assertEquals("Generate steps", state.expectedAction)
    }

    @Test
    fun `advanceToStage moves from EXECUTION to VALIDATION`() {
        // Given
        engine.startCommand("plan", "Check task")
        engine.advanceToStage("Generate steps")

        // When
        engine.advanceToStage("Validate steps")

        // Then
        val state = engine.getActiveState()
        assertEquals(CommandStage.VALIDATION, state!!.currentStage)
        assertEquals(1, state.currentStep)
        assertEquals("Validate steps", state.expectedAction)
    }

    @Test
    fun `advanceToStage moves from VALIDATION to DONE`() {
        // Given
        engine.startCommand("plan", "Check task")
        engine.advanceToStage("Generate steps")
        engine.advanceToStage("Validate steps")

        // When
        engine.advanceToStage("Save steps")

        // Then
        val state = engine.getActiveState()
        assertEquals(CommandStage.DONE, state!!.currentStage)
        assertEquals(1, state.currentStep)
        assertEquals("Save steps", state.expectedAction)
    }

    @Test
    fun `advanceToStage from DONE throws exception`() {
        // Given
        engine.startCommand("plan", "Check task")
        engine.advanceToStage("Generate steps")
        engine.advanceToStage("Validate steps")
        engine.advanceToStage("Save steps")

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            engine.advanceToStage("Next action")
        }
        assertTrue(exception.message!!.contains("Cannot advance from DONE stage"))
    }

    @Test
    fun `advanceToStage with explicit stage works`() {
        // Given
        engine.startCommand("plan", "Check task")

        // When
        engine.advanceToStage(CommandStage.EXECUTION, "Generate steps")

        // Then
        val state = engine.getActiveState()
        assertEquals(CommandStage.EXECUTION, state!!.currentStage)
    }

    @Test
    fun `advanceToStage without active command throws exception`() {
        // When & Then
        val exception = assertThrows<IllegalStateException> {
            engine.advanceToStage("action")
        }
        assertTrue(exception.message!!.contains("No active command"))
    }

    // ========== Тесты перехода между шагами ==========

    @Test
    fun `advanceStep increments step counter`() {
        // Given
        engine.startCommand("plan", "Step 1")

        // When
        engine.advanceStep("Step 2")

        // Then
        val state = engine.getActiveState()
        assertEquals(2, state!!.currentStep)
        assertEquals("Step 2", state.expectedAction)
    }

    @Test
    fun `advanceStep multiple times increments correctly`() {
        // Given
        engine.startCommand("plan", "Step 1")

        // When
        engine.advanceStep("Step 2")
        engine.advanceStep("Step 3")
        engine.advanceStep("Step 4")

        // Then
        val state = engine.getActiveState()
        assertEquals(4, state!!.currentStep)
        assertEquals("Step 4", state.expectedAction)
    }

    @Test
    fun `advanceStep without active command throws exception`() {
        // When & Then
        val exception = assertThrows<IllegalStateException> {
            engine.advanceStep("action")
        }
        assertTrue(exception.message!!.contains("No active command"))
    }

    // ========== Тесты работы с контекстом ==========

    @Test
    fun `putContext and getContext work correctly`() {
        // Given
        engine.startCommand("plan", "action")

        // When
        engine.putContext("taskId", "123")
        engine.putContext("description", "Test task")

        // Then
        assertEquals("123", engine.getContext("taskId"))
        assertEquals("Test task", engine.getContext("description"))
    }

    @Test
    fun `getContext returns null for non-existent key`() {
        // Given
        engine.startCommand("plan", "action")

        // Then
        assertNull(engine.getContext("nonExistentKey"))
    }

    @Test
    fun `putContext overwrites existing value`() {
        // Given
        engine.startCommand("plan", "action")
        engine.putContext("key", "value1")

        // When
        engine.putContext("key", "value2")

        // Then
        assertEquals("value2", engine.getContext("key"))
    }

    @Test
    fun `context preserved across stage transitions`() {
        // Given
        engine.startCommand("plan", "action")
        engine.putContext("taskId", "123")

        // When
        engine.advanceToStage("next stage")

        // Then
        assertEquals("123", engine.getContext("taskId"))
    }

    @Test
    fun `context preserved across step transitions`() {
        // Given
        engine.startCommand("plan", "action")
        engine.putContext("taskId", "123")

        // When
        engine.advanceStep("next step")

        // Then
        assertEquals("123", engine.getContext("taskId"))
    }

    @Test
    fun `putContext without active command throws exception`() {
        // When & Then
        val exception = assertThrows<IllegalStateException> {
            engine.putContext("key", "value")
        }
        assertTrue(exception.message!!.contains("No active command"))
    }

    @Test
    fun `getContext without active command throws exception`() {
        // When & Then
        val exception = assertThrows<IllegalStateException> {
            engine.getContext("key")
        }
        assertTrue(exception.message!!.contains("No active command"))
    }

    // ========== Тесты завершения команды ==========

    @Test
    fun `completeCommand destroys state`() {
        // Given
        engine.startCommand("plan", "action")

        // When
        engine.completeCommand()

        // Then
        assertFalse(engine.hasActiveCommand())
        assertNull(engine.getActiveState())
    }

    @Test
    fun `completeCommand from non-DONE stage transitions to DONE first`() {
        // Given
        engine.startCommand("plan", "action")
        engine.advanceToStage("next stage")

        // When
        engine.completeCommand()

        // Then
        assertFalse(engine.hasActiveCommand())
    }

    @Test
    fun `completeCommand without active command throws exception`() {
        // When & Then
        val exception = assertThrows<IllegalStateException> {
            engine.completeCommand()
        }
        assertTrue(exception.message!!.contains("No active command"))
    }

    // ========== Тесты отмены команды ==========

    @Test
    fun `abortCommand destroys state immediately`() {
        // Given
        engine.startCommand("plan", "action")
        engine.advanceToStage("next stage")
        engine.putContext("key", "value")

        // When
        engine.abortCommand()

        // Then
        assertFalse(engine.hasActiveCommand())
        assertNull(engine.getActiveState())
    }

    @Test
    fun `abortCommand without active command throws exception`() {
        // When & Then
        val exception = assertThrows<IllegalStateException> {
            engine.abortCommand()
        }
        assertTrue(exception.message!!.contains("No active command"))
    }

    // ========== Интеграционные тесты ==========

    @Test
    fun `full command lifecycle works correctly`() {
        // Given - Запуск команды
        engine.startCommand("plan", "Check task")
        assertTrue(engine.hasActiveCommand())

        // When - PLANNING этап
        engine.putContext("taskId", "123")
        engine.advanceStep("Request description")
        assertEquals(2, engine.getActiveState()!!.currentStep)

        // When - Переход к EXECUTION
        engine.advanceToStage("Generate steps")
        assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)
        assertEquals(1, engine.getActiveState()!!.currentStep)

        // When - EXECUTION этап
        engine.putContext("steps", "Step 1, Step 2, Step 3")
        engine.advanceStep("Parse LLM response")
        assertEquals(2, engine.getActiveState()!!.currentStep)

        // When - Переход к VALIDATION
        engine.advanceToStage("Validate steps")
        assertEquals(CommandStage.VALIDATION, engine.getActiveState()!!.currentStage)

        // When - VALIDATION этап
        engine.advanceStep("Show steps to user")
        engine.advanceStep("Wait for confirmation")
        assertEquals(3, engine.getActiveState()!!.currentStep)

        // When - Завершение команды
        engine.completeCommand()

        // Then
        assertFalse(engine.hasActiveCommand())
        assertNull(engine.getActiveState())
    }

    @Test
    fun `can start new command after completeCommand`() {
        // Given
        engine.startCommand("plan", "action1")
        engine.completeCommand()

        // When
        engine.startCommand("describe", "action2")

        // Then
        assertTrue(engine.hasActiveCommand())
        assertEquals("describe", engine.getActiveState()!!.commandName)
    }

    @Test
    fun `can start new command after abortCommand`() {
        // Given
        engine.startCommand("plan", "action1")
        engine.abortCommand()

        // When
        engine.startCommand("describe", "action2")

        // Then
        assertTrue(engine.hasActiveCommand())
        assertEquals("describe", engine.getActiveState()!!.commandName)
    }
}
