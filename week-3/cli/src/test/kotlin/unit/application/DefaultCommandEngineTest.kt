package io.averkhogliad.ai.challenge.week3.cli.unit.application

import io.averkhogliad.ai.challenge.week3.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

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
class DefaultCommandEngineTest : FreeSpec({
    lateinit var engine: DefaultCommandEngine

    beforeEach {
        engine = DefaultCommandEngine()
    }

    // ========== Тесты создания команды ==========

    "startCommand creates new command in PLANNING stage" {
        // When
        engine.startCommand("plan", "Check task")

        // Then
        engine.hasActiveCommand() shouldBe true
        val state = engine.getActiveState()
        state.shouldNotBeNull()
        state!!.commandName shouldBe "plan"
        state.currentStage shouldBe CommandStage.PLANNING
        state.currentStep shouldBe 1
        state.expectedAction shouldBe "Check task"
    }

    "startCommand with blank name throws exception" {
        // When & Then
        shouldThrow<IllegalArgumentException> {
            engine.startCommand("", "action")
        }
    }

    "startCommand when another command active throws exception" {
        // Given
        engine.startCommand("plan", "action1")

        // When & Then
        val exception = shouldThrow<IllegalStateException> {
            engine.startCommand("describe", "action2")
        }
        exception.message shouldContain "another command 'plan' is already active"
    }

    // ========== Тесты проверки активной команды ==========

    "hasActiveCommand returns false when no command started" {
        // Then
        engine.hasActiveCommand() shouldBe false
    }

    "hasActiveCommand returns true after startCommand" {
        // Given
        engine.startCommand("plan", "action")

        // Then
        engine.hasActiveCommand() shouldBe true
    }

    "getActiveState returns null when no command started" {
        // Then
        engine.getActiveState().shouldBeNull()
    }

    "getActiveState returns state after startCommand" {
        // Given
        engine.startCommand("plan", "action")

        // Then
        val state = engine.getActiveState()
        state.shouldNotBeNull()
        state!!.commandName shouldBe "plan"
    }

    // ========== Тесты перехода между этапами ==========

    "advanceToStage moves from PLANNING to EXECUTION" {
        // Given
        engine.startCommand("plan", "Check task")

        // When
        engine.advanceToStage("Generate steps")

        // Then
        val state = engine.getActiveState()
        state!!.currentStage shouldBe CommandStage.EXECUTION
        state.currentStep shouldBe 1 // Сброс шага на 1
        state.expectedAction shouldBe "Generate steps"
    }

    "advanceToStage moves from EXECUTION to VALIDATION" {
        // Given
        engine.startCommand("plan", "Check task")
        engine.advanceToStage("Generate steps")

        // When
        engine.advanceToStage("Validate steps")

        // Then
        val state = engine.getActiveState()
        state!!.currentStage shouldBe CommandStage.VALIDATION
        state.currentStep shouldBe 1
        state.expectedAction shouldBe "Validate steps"
    }

    "advanceToStage moves from VALIDATION to DONE" {
        // Given
        engine.startCommand("plan", "Check task")
        engine.advanceToStage("Generate steps")
        engine.advanceToStage("Validate steps")

        // When
        engine.advanceToStage("Save steps")

        // Then
        val state = engine.getActiveState()
        state!!.currentStage shouldBe CommandStage.DONE
        state.currentStep shouldBe 1
        state.expectedAction shouldBe "Save steps"
    }

    "advanceToStage from DONE throws exception" {
        // Given
        engine.startCommand("plan", "Check task")
        engine.advanceToStage("Generate steps")
        engine.advanceToStage("Validate steps")
        engine.advanceToStage("Save steps")

        // When & Then
        val exception = shouldThrow<IllegalStateException> {
            engine.advanceToStage("Next action")
        }
        exception.message shouldContain "Cannot advance from DONE stage"
    }

    "advanceToStage with explicit stage works" {
        // Given
        engine.startCommand("plan", "Check task")

        // When
        engine.advanceToStage(CommandStage.EXECUTION, "Generate steps")

        // Then
        val state = engine.getActiveState()
        state!!.currentStage shouldBe CommandStage.EXECUTION
    }

    "advanceToStage without active command throws exception" {
        // When & Then
        val exception = shouldThrow<IllegalStateException> {
            engine.advanceToStage("action")
        }
        exception.message shouldContain "No active command"
    }

    // ========== Тесты перехода между шагами ==========

    "advanceStep increments step counter" {
        // Given
        engine.startCommand("plan", "Step 1")

        // When
        engine.advanceStep("Step 2")

        // Then
        val state = engine.getActiveState()
        state!!.currentStep shouldBe 2
        state.expectedAction shouldBe "Step 2"
    }

    "advanceStep multiple times increments correctly" {
        // Given
        engine.startCommand("plan", "Step 1")

        // When
        engine.advanceStep("Step 2")
        engine.advanceStep("Step 3")
        engine.advanceStep("Step 4")

        // Then
        val state = engine.getActiveState()
        state!!.currentStep shouldBe 4
        state.expectedAction shouldBe "Step 4"
    }

    "advanceStep without active command throws exception" {
        // When & Then
        val exception = shouldThrow<IllegalStateException> {
            engine.advanceStep("action")
        }
        exception.message shouldContain "No active command"
    }

    // ========== Тесты работы с контекстом ==========

    "putContext and getContext work correctly" {
        // Given
        engine.startCommand("plan", "action")

        // When
        engine.putContext("taskId", "123")
        engine.putContext("description", "Test task")

        // Then
        engine.getContext("taskId") shouldBe "123"
        engine.getContext("description") shouldBe "Test task"
    }

    "getContext returns null for non-existent key" {
        // Given
        engine.startCommand("plan", "action")

        // Then
        engine.getContext("nonExistentKey").shouldBeNull()
    }

    "putContext overwrites existing value" {
        // Given
        engine.startCommand("plan", "action")
        engine.putContext("key", "value1")

        // When
        engine.putContext("key", "value2")

        // Then
        engine.getContext("key") shouldBe "value2"
    }

    "context preserved across stage transitions" {
        // Given
        engine.startCommand("plan", "action")
        engine.putContext("taskId", "123")

        // When
        engine.advanceToStage("next stage")

        // Then
        engine.getContext("taskId") shouldBe "123"
    }

    "context preserved across step transitions" {
        // Given
        engine.startCommand("plan", "action")
        engine.putContext("taskId", "123")

        // When
        engine.advanceStep("next step")

        // Then
        engine.getContext("taskId") shouldBe "123"
    }

    "putContext without active command throws exception" {
        // When & Then
        val exception = shouldThrow<IllegalStateException> {
            engine.putContext("key", "value")
        }
        exception.message shouldContain "No active command"
    }

    "getContext without active command throws exception" {
        // When & Then
        val exception = shouldThrow<IllegalStateException> {
            engine.getContext("key")
        }
        exception.message shouldContain "No active command"
    }

    // ========== Тесты завершения команды ==========

    "completeCommand destroys state" {
        // Given
        engine.startCommand("plan", "action")

        // When
        engine.completeCommand()

        // Then
        engine.hasActiveCommand() shouldBe false
        engine.getActiveState().shouldBeNull()
    }

    "completeCommand from non-DONE stage transitions to DONE first" {
        // Given
        engine.startCommand("plan", "action")
        engine.advanceToStage("next stage")

        // When
        engine.completeCommand()

        // Then
        engine.hasActiveCommand() shouldBe false
    }

    "completeCommand without active command throws exception" {
        // When & Then
        val exception = shouldThrow<IllegalStateException> {
            engine.completeCommand()
        }
        exception.message shouldContain "No active command"
    }

    // ========== Тесты отмены команды ==========

    "abortCommand destroys state immediately" {
        // Given
        engine.startCommand("plan", "action")
        engine.advanceToStage("next stage")
        engine.putContext("key", "value")

        // When
        engine.abortCommand()

        // Then
        engine.hasActiveCommand() shouldBe false
        engine.getActiveState().shouldBeNull()
    }

    "abortCommand without active command throws exception" {
        // When & Then
        val exception = shouldThrow<IllegalStateException> {
            engine.abortCommand()
        }
        exception.message shouldContain "No active command"
    }

    // ========== Интеграционные тесты ==========

    "full command lifecycle works correctly" {
        // Given - Запуск команды
        engine.startCommand("plan", "Check task")
        engine.hasActiveCommand() shouldBe true

        // When - PLANNING этап
        engine.putContext("taskId", "123")
        engine.advanceStep("Request description")
        engine.getActiveState()!!.currentStep shouldBe 2

        // When - Переход к EXECUTION
        engine.advanceToStage("Generate steps")
        engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        engine.getActiveState()!!.currentStep shouldBe 1

        // When - EXECUTION этап
        engine.putContext("steps", "Step 1, Step 2, Step 3")
        engine.advanceStep("Parse LLM response")
        engine.getActiveState()!!.currentStep shouldBe 2

        // When - Переход к VALIDATION
        engine.advanceToStage("Validate steps")
        engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION

        // When - VALIDATION этап
        engine.advanceStep("Show steps to user")
        engine.advanceStep("Wait for confirmation")
        engine.getActiveState()!!.currentStep shouldBe 3

        // When - Завершение команды
        engine.completeCommand()

        // Then
        engine.hasActiveCommand() shouldBe false
        engine.getActiveState().shouldBeNull()
    }

    "can start new command after completeCommand" {
        // Given
        engine.startCommand("plan", "action1")
        engine.completeCommand()

        // When
        engine.startCommand("describe", "action2")

        // Then
        engine.hasActiveCommand() shouldBe true
        engine.getActiveState()!!.commandName shouldBe "describe"
    }

    "can start new command after abortCommand" {
        // Given
        engine.startCommand("plan", "action1")
        engine.abortCommand()

        // When
        engine.startCommand("describe", "action2")

        // Then
        engine.hasActiveCommand() shouldBe true
        engine.getActiveState()!!.commandName shouldBe "describe"
    }
})
