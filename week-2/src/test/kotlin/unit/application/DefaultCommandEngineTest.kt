package io.averkhogliad.ai.challenge.week2.unit.application

import io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class DefaultCommandEngineTest : FreeSpec({

    lateinit var engine: DefaultCommandEngine

    beforeEach {
        engine = DefaultCommandEngine()
    }

    "startCommand" - {
        "should create new command in PLANNING stage" {
            engine.startCommand("plan", "Check task")

            engine.hasActiveCommand() shouldBe true
            val state = engine.getActiveState()
            state.shouldNotBeNull()
            state!!.commandName shouldBe "plan"
            state.currentStage shouldBe CommandStage.PLANNING
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Check task"
        }

        "should throw exception when name is blank" {
            shouldThrow<IllegalArgumentException> {
                engine.startCommand("", "action")
            }
        }

        "should throw exception when another command is already active" {
            engine.startCommand("plan", "action1")

            val exception = shouldThrow<IllegalStateException> {
                engine.startCommand("describe", "action2")
            }
            exception.message shouldContain "another command 'plan' is already active"
        }
    }

    "hasActiveCommand" - {
        "should return false when no command started" {
            engine.hasActiveCommand() shouldBe false
        }

        "should return true after startCommand" {
            engine.startCommand("plan", "action")
            engine.hasActiveCommand() shouldBe true
        }
    }

    "getActiveState" - {
        "should return null when no command started" {
            engine.getActiveState().shouldBeNull()
        }

        "should return state after startCommand" {
            engine.startCommand("plan", "action")
            val state = engine.getActiveState()
            state.shouldNotBeNull()
            state!!.commandName shouldBe "plan"
        }
    }

    "advanceToStage" - {
        "should move from PLANNING to EXECUTION" {
            engine.startCommand("plan", "Check task")

            engine.advanceToStage("Generate steps")

            val state = engine.getActiveState()
            state!!.currentStage shouldBe CommandStage.EXECUTION
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Generate steps"
        }

        "should move from EXECUTION to VALIDATION" {
            engine.startCommand("plan", "Check task")
            engine.advanceToStage("Generate steps")

            engine.advanceToStage("Validate steps")

            val state = engine.getActiveState()
            state!!.currentStage shouldBe CommandStage.VALIDATION
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Validate steps"
        }

        "should move from VALIDATION to DONE" {
            engine.startCommand("plan", "Check task")
            engine.advanceToStage("Generate steps")
            engine.advanceToStage("Validate steps")

            engine.advanceToStage("Save steps")

            val state = engine.getActiveState()
            state!!.currentStage shouldBe CommandStage.DONE
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Save steps"
        }

        "should throw exception when advancing from DONE" {
            engine.startCommand("plan", "Check task")
            engine.advanceToStage("Generate steps")
            engine.advanceToStage("Validate steps")
            engine.advanceToStage("Save steps")

            val exception = shouldThrow<IllegalStateException> {
                engine.advanceToStage("Next action")
            }
            exception.message shouldContain "Cannot advance from DONE stage"
        }

        "should work with explicit stage parameter" {
            engine.startCommand("plan", "Check task")

            engine.advanceToStage(CommandStage.EXECUTION, "Generate steps")

            val state = engine.getActiveState()
            state!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "should throw exception without active command" {
            val exception = shouldThrow<IllegalStateException> {
                engine.advanceToStage("action")
            }
            exception.message shouldContain "No active command"
        }
    }

    "advanceStep" - {
        "should increment step counter" {
            engine.startCommand("plan", "Step 1")

            engine.advanceStep("Step 2")

            val state = engine.getActiveState()
            state!!.currentStep shouldBe 2
            state.expectedAction shouldBe "Step 2"
        }

        "should increment correctly across multiple advances" {
            engine.startCommand("plan", "Step 1")

            engine.advanceStep("Step 2")
            engine.advanceStep("Step 3")
            engine.advanceStep("Step 4")

            val state = engine.getActiveState()
            state!!.currentStep shouldBe 4
            state.expectedAction shouldBe "Step 4"
        }

        "should throw exception without active command" {
            val exception = shouldThrow<IllegalStateException> {
                engine.advanceStep("action")
            }
            exception.message shouldContain "No active command"
        }
    }

    "putContext and getContext" - {
        "should store and retrieve values correctly" {
            engine.startCommand("plan", "action")

            engine.putContext("taskId", "123")
            engine.putContext("description", "Test task")

            engine.getContext("taskId") shouldBe "123"
            engine.getContext("description") shouldBe "Test task"
        }

        "should return null for non-existent key" {
            engine.startCommand("plan", "action")

            engine.getContext("nonExistentKey").shouldBeNull()
        }

        "should overwrite existing value" {
            engine.startCommand("plan", "action")
            engine.putContext("key", "value1")

            engine.putContext("key", "value2")

            engine.getContext("key") shouldBe "value2"
        }

        "should preserve context across stage transitions" {
            engine.startCommand("plan", "action")
            engine.putContext("taskId", "123")

            engine.advanceToStage("next stage")

            engine.getContext("taskId") shouldBe "123"
        }

        "should preserve context across step transitions" {
            engine.startCommand("plan", "action")
            engine.putContext("taskId", "123")

            engine.advanceStep("next step")

            engine.getContext("taskId") shouldBe "123"
        }

        "putContext should throw exception without active command" {
            val exception = shouldThrow<IllegalStateException> {
                engine.putContext("key", "value")
            }
            exception.message shouldContain "No active command"
        }

        "getContext should throw exception without active command" {
            val exception = shouldThrow<IllegalStateException> {
                engine.getContext("key")
            }
            exception.message shouldContain "No active command"
        }
    }

    "completeCommand" - {
        "should destroy state" {
            engine.startCommand("plan", "action")

            engine.completeCommand()

            engine.hasActiveCommand() shouldBe false
            engine.getActiveState().shouldBeNull()
        }

        "should work from non-DONE stage by transitioning to DONE first" {
            engine.startCommand("plan", "action")
            engine.advanceToStage("next stage")

            engine.completeCommand()

            engine.hasActiveCommand() shouldBe false
        }

        "should throw exception without active command" {
            val exception = shouldThrow<IllegalStateException> {
                engine.completeCommand()
            }
            exception.message shouldContain "No active command"
        }
    }

    "abortCommand" - {
        "should destroy state immediately" {
            engine.startCommand("plan", "action")
            engine.advanceToStage("next stage")
            engine.putContext("key", "value")

            engine.abortCommand()

            engine.hasActiveCommand() shouldBe false
            engine.getActiveState().shouldBeNull()
        }

        "should throw exception without active command" {
            val exception = shouldThrow<IllegalStateException> {
                engine.abortCommand()
            }
            exception.message shouldContain "No active command"
        }
    }

    "full command lifecycle" - {
        "should work correctly from start to completion" {
            engine.startCommand("plan", "Check task")
            engine.hasActiveCommand() shouldBe true

            engine.putContext("taskId", "123")
            engine.advanceStep("Request description")
            engine.getActiveState()!!.currentStep shouldBe 2

            engine.advanceToStage("Generate steps")
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
            engine.getActiveState()!!.currentStep shouldBe 1

            engine.putContext("steps", "Step 1, Step 2, Step 3")
            engine.advanceStep("Parse LLM response")
            engine.getActiveState()!!.currentStep shouldBe 2

            engine.advanceToStage("Validate steps")
            engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION

            engine.advanceStep("Show steps to user")
            engine.advanceStep("Wait for confirmation")
            engine.getActiveState()!!.currentStep shouldBe 3

            engine.completeCommand()

            engine.hasActiveCommand() shouldBe false
            engine.getActiveState().shouldBeNull()
        }
    }

    "can start new command after completeCommand" {
        engine.startCommand("plan", "action1")
        engine.completeCommand()

        engine.startCommand("describe", "action2")

        engine.hasActiveCommand() shouldBe true
        engine.getActiveState()!!.commandName shouldBe "describe"
    }

    "can start new command after abortCommand" {
        engine.startCommand("plan", "action1")
        engine.abortCommand()

        engine.startCommand("describe", "action2")

        engine.hasActiveCommand() shouldBe true
        engine.getActiveState()!!.commandName shouldBe "describe"
    }
})
