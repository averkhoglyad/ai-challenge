package io.averkhogliad.ai.challenge.week2.unit.application

import io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Тесты для полного цикла FSM команды :plan.
 *
 * Проверяет полный жизненный цикл команды от создания до завершения,
 * включая все переходы между этапами и шагами.
 */
class PlanCommandFsmTest : FreeSpec({

    lateinit var engine: DefaultCommandEngine

    beforeEach {
        engine = DefaultCommandEngine()
    }

    "full lifecycle" - {

        "completes full PLANNING to DONE lifecycle" {
            // given
            engine.startCommand("plan", "Check open task")
            engine.hasActiveCommand().shouldBeTrue()
            val initialState = engine.getActiveState().shouldNotBeNull()
            initialState.commandName shouldBe "plan"
            initialState.currentStage shouldBe CommandStage.PLANNING
            initialState.currentStep shouldBe 1

            // when — PLANNING: сохраняем taskId в контекст
            engine.putContext("taskId", "task-123")
            engine.putContext("description", "Implement feature X")

            // when — Переход к EXECUTION
            engine.advanceToStage("Send LLM request for plan generation")
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION
            engine.getActiveState()!!.currentStep shouldBe 1

            // when — EXECUTION: парсим ответ LLM
            engine.putContext("llmResponse", "Step 1: Analyze requirements\nStep 2: Write code")
            engine.advanceStep("Parse LLM response")
            engine.getActiveState()!!.currentStep shouldBe 2

            // when — Переход к VALIDATION
            engine.advanceToStage("Show plan to user for confirmation")
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.VALIDATION
            engine.getActiveState()!!.currentStep shouldBe 1

            // when — VALIDATION: пользователь подтверждает
            engine.advanceStep("User confirmed plan")
            engine.getActiveState()!!.currentStep shouldBe 2

            // when — Завершение команды
            engine.completeCommand()

            // then
            engine.hasActiveCommand().shouldBeFalse()
            engine.getActiveState().shouldBeNull()
        }
    }

    "abort" - {

        "aborts at PLANNING stage" {
            // given
            engine.startCommand("plan", "Check open task")
            engine.putContext("taskId", "task-123")
            engine.hasActiveCommand().shouldBeTrue()

            // when
            engine.abortCommand()

            // then
            engine.hasActiveCommand().shouldBeFalse()
            engine.getActiveState().shouldBeNull()
        }

        "aborts at EXECUTION stage" {
            // given
            engine.startCommand("plan", "Check open task")
            engine.advanceToStage("Send LLM request")
            engine.putContext("taskId", "task-123")
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION

            // when
            engine.abortCommand()

            // then
            engine.hasActiveCommand().shouldBeFalse()
        }

        "aborts at VALIDATION stage" {
            // given
            engine.startCommand("plan", "Check open task")
            engine.advanceToStage("Generate plan")
            engine.advanceToStage("Show plan to user")
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.VALIDATION

            // when
            engine.abortCommand()

            // then
            engine.hasActiveCommand().shouldBeFalse()
        }
    }

    "context preservation" - {

        "preserves context across all stages of plan command" {
            // given
            engine.startCommand("plan", "Check open task")
            engine.putContext("taskId", "task-123")
            engine.putContext("profileName", "default")

            // when — проходим все этапы
            engine.advanceToStage("Generate plan")
            engine.putContext("stepCount", "3")

            engine.advanceToStage("Validate plan")
            engine.putContext("userConfirmed", "true")

            // then — все контексты сохранены
            engine.getContext("taskId") shouldBe "task-123"
            engine.getContext("profileName") shouldBe "default"
            engine.getContext("stepCount") shouldBe "3"
            engine.getContext("userConfirmed") shouldBe "true"
        }
    }

    "restart" - {

        "can start new plan command after previous completed" {
            // given
            engine.startCommand("plan", "Check task 1")
            engine.completeCommand()

            // when
            engine.startCommand("plan", "Check task 2")

            // then
            engine.hasActiveCommand().shouldBeTrue()
            val state = engine.getActiveState().shouldNotBeNull()
            state.commandName shouldBe "plan"
            state.currentStage shouldBe CommandStage.PLANNING
        }

        "cannot start new plan command while one is active" {
            // given
            engine.startCommand("plan", "Check task 1")

            // when & then
            shouldThrow<IllegalStateException> {
                engine.startCommand("plan", "Check task 2")
            }
        }
    }

    "step counter" - {

        "resets when advancing to new stage" {
            // given
            engine.startCommand("plan", "Step 1")
            engine.advanceStep("Step 2")
            engine.advanceStep("Step 3")
            engine.getActiveState()!!.currentStep shouldBe 3

            // when — переход к новому этапу
            engine.advanceToStage("New stage action")

            // then — счётчик сброшен
            engine.getActiveState()!!.currentStep shouldBe 1
        }
    }
})
