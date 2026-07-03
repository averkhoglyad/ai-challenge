package io.averkhogliad.ai.challenge.week3.cli.it

import io.averkhogliad.ai.challenge.week3.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Integration-тесты для полного цикла FSM команды :plan.
 *
 * Проверяет полный жизненный цикл команды от создания до завершения,
 * включая все переходы между этапами и шагами.
 */
class PlanCommandFsmIT : FreeSpec({

    lateinit var engine: DefaultCommandEngine

    beforeTest {
        engine = DefaultCommandEngine()
    }

    "full plan command lifecycle - PLANNING to DONE" {
        // Given - Запуск команды plan
        engine.startCommand("plan", "Check open task")
        engine.hasActiveCommand() shouldBe true
        engine.getActiveState()!!.commandName shouldBe "plan"
        engine.getActiveState()!!.currentStage shouldBe CommandStage.PLANNING
        engine.getActiveState()!!.currentStep shouldBe 1

        // When - PLANNING: сохраняем taskId в контекст
        engine.putContext("taskId", "task-123")
        engine.putContext("description", "Implement feature X")

        // When - Переход к EXECUTION
        engine.advanceToStage("Send LLM request for plan generation")
        engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        engine.getActiveState()!!.currentStep shouldBe 1

        // When - EXECUTION: парсим ответ LLM
        engine.putContext("llmResponse", "Step 1: Analyze requirements\nStep 2: Write code")
        engine.advanceStep("Parse LLM response")
        engine.getActiveState()!!.currentStep shouldBe 2

        // When - Переход к VALIDATION
        engine.advanceToStage("Show plan to user for confirmation")
        engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION
        engine.getActiveState()!!.currentStep shouldBe 1

        // When - VALIDATION: пользователь подтверждает
        engine.advanceStep("User confirmed plan")
        engine.getActiveState()!!.currentStep shouldBe 2

        // When - Завершение команды
        engine.completeCommand()

        // Then
        engine.hasActiveCommand() shouldBe false
        engine.getActiveState() shouldBe null
    }

    "plan command abort at PLANNING stage" {
        // Given
        engine.startCommand("plan", "Check open task")
        engine.putContext("taskId", "task-123")
        engine.hasActiveCommand() shouldBe true

        // When - Пользователь отменяет команду
        engine.abortCommand()

        // Then
        engine.hasActiveCommand() shouldBe false
        engine.getActiveState() shouldBe null
    }

    "plan command abort at EXECUTION stage" {
        // Given
        engine.startCommand("plan", "Check open task")
        engine.advanceToStage("Send LLM request")
        engine.putContext("taskId", "task-123")
        engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION

        // When
        engine.abortCommand()

        // Then
        engine.hasActiveCommand() shouldBe false
    }

    "plan command abort at VALIDATION stage" {
        // Given
        engine.startCommand("plan", "Check open task")
        engine.advanceToStage("Generate plan")
        engine.advanceToStage("Show plan to user")
        engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION

        // When
        engine.abortCommand()

        // Then
        engine.hasActiveCommand() shouldBe false
    }

    "context is preserved across all stages of plan command" {
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
        engine.getContext("taskId") shouldBe "task-123"
        engine.getContext("profileName") shouldBe "default"
        engine.getContext("stepCount") shouldBe "3"
        engine.getContext("userConfirmed") shouldBe "true"
    }

    "can start new plan command after previous completed" {
        // Given - первая команда
        engine.startCommand("plan", "Check task 1")
        engine.completeCommand()

        // When - вторая команда
        engine.startCommand("plan", "Check task 2")

        // Then
        engine.hasActiveCommand() shouldBe true
        engine.getActiveState()!!.commandName shouldBe "plan"
        engine.getActiveState()!!.currentStage shouldBe CommandStage.PLANNING
    }

    "cannot start new plan command while one is active" {
        // Given
        engine.startCommand("plan", "Check task 1")

        // When & Then
        shouldThrow<IllegalStateException> {
            engine.startCommand("plan", "Check task 2")
        }
    }

    "step counter resets when advancing to new stage" {
        // Given
        engine.startCommand("plan", "Step 1")
        engine.advanceStep("Step 2")
        engine.advanceStep("Step 3")
        engine.getActiveState()!!.currentStep shouldBe 3

        // When - переход к новому этапу
        engine.advanceToStage("New stage action")

        // Then - счётчик сброшен
        engine.getActiveState()!!.currentStep shouldBe 1
    }
})
