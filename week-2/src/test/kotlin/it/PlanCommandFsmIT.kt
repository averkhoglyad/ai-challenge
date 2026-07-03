package io.averkhogliad.ai.challenge.week2.it

import io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration-тесты для полного цикла FSM команды :plan.
 * 
 * Проверяет полный жизненный цикл команды от создания до завершения,
 * включая все переходы между этапами и шагами.
 */
class PlanCommandFsmIT {

    private lateinit var engine: DefaultCommandEngine

    @BeforeEach
    fun setUp() {
        engine = DefaultCommandEngine()
    }

    @Test
    fun `full plan command lifecycle - PLANNING to DONE`() {
        // Given - Запуск команды plan
        engine.startCommand("plan", "Check open task")
        assertTrue(engine.hasActiveCommand())
        assertEquals("plan", engine.getActiveState()!!.commandName)
        assertEquals(CommandStage.PLANNING, engine.getActiveState()!!.currentStage)
        assertEquals(1, engine.getActiveState()!!.currentStep)

        // When - PLANNING: сохраняем taskId в контекст
        engine.putContext("taskId", "task-123")
        engine.putContext("description", "Implement feature X")

        // When - Переход к EXECUTION
        engine.advanceToStage("Send LLM request for plan generation")
        assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)
        assertEquals(1, engine.getActiveState()!!.currentStep)

        // When - EXECUTION: парсим ответ LLM
        engine.putContext("llmResponse", "Step 1: Analyze requirements\nStep 2: Write code")
        engine.advanceStep("Parse LLM response")
        assertEquals(2, engine.getActiveState()!!.currentStep)

        // When - Переход к VALIDATION
        engine.advanceToStage("Show plan to user for confirmation")
        assertEquals(CommandStage.VALIDATION, engine.getActiveState()!!.currentStage)
        assertEquals(1, engine.getActiveState()!!.currentStep)

        // When - VALIDATION: пользователь подтверждает
        engine.advanceStep("User confirmed plan")
        assertEquals(2, engine.getActiveState()!!.currentStep)

        // When - Завершение команды
        engine.completeCommand()

        // Then
        assertFalse(engine.hasActiveCommand())
        assertNull(engine.getActiveState())
    }

    @Test
    fun `plan command abort at PLANNING stage`() {
        // Given
        engine.startCommand("plan", "Check open task")
        engine.putContext("taskId", "task-123")
        assertTrue(engine.hasActiveCommand())

        // When - Пользователь отменяет команду
        engine.abortCommand()

        // Then
        assertFalse(engine.hasActiveCommand())
        assertNull(engine.getActiveState())
    }

    @Test
    fun `plan command abort at EXECUTION stage`() {
        // Given
        engine.startCommand("plan", "Check open task")
        engine.advanceToStage("Send LLM request")
        engine.putContext("taskId", "task-123")
        assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)

        // When
        engine.abortCommand()

        // Then
        assertFalse(engine.hasActiveCommand())
    }

    @Test
    fun `plan command abort at VALIDATION stage`() {
        // Given
        engine.startCommand("plan", "Check open task")
        engine.advanceToStage("Generate plan")
        engine.advanceToStage("Show plan to user")
        assertEquals(CommandStage.VALIDATION, engine.getActiveState()!!.currentStage)

        // When
        engine.abortCommand()

        // Then
        assertFalse(engine.hasActiveCommand())
    }

    @Test
    fun `context is preserved across all stages of plan command`() {
        // Given
        engine.startCommand("plan", "Check open task")
        engine.putContext("taskId", "task-123")
        engine.putContext("profileName", "default")

        // When - проходим все этапы
        engine.advanceToStage("Generate plan")
        engine.putContext("stepCount", "3")

        engine.advanceToStage("Validate plan")
        engine.putContext("userConfirmed", "true")

        // Then - все контексты сохранены
        assertEquals("task-123", engine.getContext("taskId"))
        assertEquals("default", engine.getContext("profileName"))
        assertEquals("3", engine.getContext("stepCount"))
        assertEquals("true", engine.getContext("userConfirmed"))
    }

    @Test
    fun `can start new plan command after previous completed`() {
        // Given - первая команда
        engine.startCommand("plan", "Check task 1")
        engine.completeCommand()

        // When - вторая команда
        engine.startCommand("plan", "Check task 2")

        // Then
        assertTrue(engine.hasActiveCommand())
        assertEquals("plan", engine.getActiveState()!!.commandName)
        assertEquals(CommandStage.PLANNING, engine.getActiveState()!!.currentStage)
    }

    @Test
    fun `cannot start new plan command while one is active`() {
        // Given
        engine.startCommand("plan", "Check task 1")

        // When & Then
        assertThrows(IllegalStateException::class.java) {
            engine.startCommand("plan", "Check task 2")
        }
    }

    @Test
    fun `step counter resets when advancing to new stage`() {
        // Given
        engine.startCommand("plan", "Step 1")
        engine.advanceStep("Step 2")
        engine.advanceStep("Step 3")
        assertEquals(3, engine.getActiveState()!!.currentStep)

        // When - переход к новому этапу
        engine.advanceToStage("New stage action")

        // Then - счётчик сброшен
        assertEquals(1, engine.getActiveState()!!.currentStep)
    }
}
