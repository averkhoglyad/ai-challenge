package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.CommandState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Тесты для модели состояния команды [CommandState].
 */
class CommandStateTest : FreeSpec({

    "creation" - {

        "should create CommandState with valid data" {
            // when
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 1,
                expectedAction = "Check open task"
            )

            // then
            state.commandName shouldBe "plan"
            state.currentStage shouldBe CommandStage.PLANNING
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Check open task"
            state.context.isEmpty() shouldBe true
        }
    }

    "validation" - {

        "should throw exception when commandName is blank" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                CommandState(
                    commandName = "",
                    currentStage = CommandStage.PLANNING
                )
            }
        }

        "should throw exception when currentStep is less than 1" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                CommandState(
                    commandName = "plan",
                    currentStage = CommandStage.PLANNING,
                    currentStep = 0
                )
            }
        }
    }

    "advanceToStage" - {

        "should advance to next stage" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 3,
                expectedAction = "Old action"
            )

            // when
            val advanced = state.advanceToStage(CommandStage.EXECUTION, "Send LLM request")

            // then
            advanced.currentStage shouldBe CommandStage.EXECUTION
            advanced.currentStep shouldBe 1
            advanced.expectedAction shouldBe "Send LLM request"
        }

        "should preserve context when advancing stage" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING
            ).putContext("key", "value")

            // when
            val advanced = state.advanceToStage(CommandStage.EXECUTION)

            // then
            advanced.getContext("key") shouldBe "value"
        }
    }

    "advanceStep" - {

        "should advance step within stage" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.EXECUTION,
                currentStep = 1,
                expectedAction = "Step 1"
            )

            // when
            val advanced = state.advanceStep("Step 2")

            // then
            advanced.currentStage shouldBe CommandStage.EXECUTION
            advanced.currentStep shouldBe 2
            advanced.expectedAction shouldBe "Step 2"
        }

        "should preserve context when advancing step" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.EXECUTION,
                currentStep = 1
            ).putContext("key", "value")

            // when
            val advanced = state.advanceStep()

            // then
            advanced.getContext("key") shouldBe "value"
        }
    }

    "context" - {

        "should put and get context values" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING
            )

            // when
            val withContext = state
                .putContext("taskId", "123")
                .putContext("description", "Test description")

            // then
            withContext.getContext("taskId") shouldBe "123"
            withContext.getContext("description") shouldBe "Test description"
        }

        "should return null for missing context key" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING
            )

            // when
            val result = state.getContext("nonexistent")

            // then
            result shouldBe null
        }
    }

    "isDone" - {

        "should check if command is done" {
            // given
            val planningState = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING
            )
            val doneState = CommandState(
                commandName = "plan",
                currentStage = CommandStage.DONE
            )

            // then
            planningState.isDone() shouldBe false
            doneState.isDone() shouldBe true
        }
    }
})
