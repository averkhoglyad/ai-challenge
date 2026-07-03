package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CommandStateTest : FreeSpec({

    "CommandState" - {

        "should create CommandState with valid data" {
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 1,
                expectedAction = "Check open task"
            )

            state.commandName shouldBe "plan"
            state.currentStage shouldBe CommandStage.PLANNING
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Check open task"
            state.context.isEmpty() shouldBe true
        }

        "should throw exception when commandName is blank" {
            shouldThrow<IllegalArgumentException> {
                CommandState(
                    commandName = "",
                    currentStage = CommandStage.PLANNING
                )
            }
        }

        "should throw exception when currentStep is less than 1" {
            shouldThrow<IllegalArgumentException> {
                CommandState(
                    commandName = "plan",
                    currentStage = CommandStage.PLANNING,
                    currentStep = 0
                )
            }
        }

        "should advance to next stage" {
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 3,
                expectedAction = "Old action"
            )

            val advanced = state.advanceToStage(CommandStage.EXECUTION, "Send LLM request")

            advanced.currentStage shouldBe CommandStage.EXECUTION
            advanced.currentStep shouldBe 1
            advanced.expectedAction shouldBe "Send LLM request"
        }

        "should advance step within stage" {
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.EXECUTION,
                currentStep = 1,
                expectedAction = "Step 1"
            )

            val advanced = state.advanceStep("Step 2")

            advanced.currentStage shouldBe CommandStage.EXECUTION
            advanced.currentStep shouldBe 2
            advanced.expectedAction shouldBe "Step 2"
        }

        "should put and get context values" {
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING
            )

            val withContext = state
                .putContext("taskId", "123")
                .putContext("description", "Test description")

            withContext.getContext("taskId") shouldBe "123"
            withContext.getContext("description") shouldBe "Test description"
        }

        "should return null for missing context key" {
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING
            )

            state.getContext("nonexistent") shouldBe null
        }

        "should check if command is done" {
            val planningState = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING
            )
            planningState.isDone() shouldBe false

            val doneState = CommandState(
                commandName = "plan",
                currentStage = CommandStage.DONE
            )
            doneState.isDone() shouldBe true
        }

        "should preserve context when advancing stage" {
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING
            ).putContext("key", "value")

            val advanced = state.advanceToStage(CommandStage.EXECUTION)

            advanced.getContext("key") shouldBe "value"
        }

        "should preserve context when advancing step" {
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.EXECUTION,
                currentStep = 1
            ).putContext("key", "value")

            val advanced = state.advanceStep()

            advanced.getContext("key") shouldBe "value"
        }
    }
})
