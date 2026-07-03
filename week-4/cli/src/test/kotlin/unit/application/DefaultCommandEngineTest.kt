package io.averkhogliad.ai.challenge.week4.cli.unit.application

import io.averkhogliad.ai.challenge.week4.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week4.cli.domain.model.CommandStage
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

    "startCommand" - {
        "creates new command in PLANNING stage" {
            // when
            engine.startCommand("plan", "Check task")

            // then
            engine.hasActiveCommand() shouldBe true
            val state = engine.getActiveState()
            state.shouldNotBeNull()
            state!!.commandName shouldBe "plan"
            state.currentStage shouldBe CommandStage.PLANNING
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Check task"
        }

        "with blank name throws exception" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                engine.startCommand("", "action")
            }
        }

        "when another command active throws exception" {
            // given
            engine.startCommand("plan", "action1")

            // when & then
            val exception = shouldThrow<IllegalStateException> {
                engine.startCommand("describe", "action2")
            }
            exception.message shouldContain "another command 'plan' is already active"
        }
    }

    "hasActiveCommand" - {
        "returns false when no command started" {
            // then
            engine.hasActiveCommand() shouldBe false
        }

        "returns true after startCommand" {
            // given
            engine.startCommand("plan", "action")

            // then
            engine.hasActiveCommand() shouldBe true
        }
    }

    "getActiveState" - {
        "returns null when no command started" {
            // then
            engine.getActiveState().shouldBeNull()
        }

        "returns state after startCommand" {
            // given
            engine.startCommand("plan", "action")

            // then
            val state = engine.getActiveState()
            state.shouldNotBeNull()
            state!!.commandName shouldBe "plan"
        }
    }

    "advanceToStage" - {
        "moves from PLANNING to EXECUTION" {
            // given
            engine.startCommand("plan", "Check task")

            // when
            engine.advanceToStage("Generate steps")

            // then
            val state = engine.getActiveState()
            state!!.currentStage shouldBe CommandStage.EXECUTION
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Generate steps"
        }

        "moves from EXECUTION to VALIDATION" {
            // given
            engine.startCommand("plan", "Check task")
            engine.advanceToStage("Generate steps")

            // when
            engine.advanceToStage("Validate steps")

            // then
            val state = engine.getActiveState()
            state!!.currentStage shouldBe CommandStage.VALIDATION
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Validate steps"
        }

        "moves from VALIDATION to DONE" {
            // given
            engine.startCommand("plan", "Check task")
            engine.advanceToStage("Generate steps")
            engine.advanceToStage("Validate steps")

            // when
            engine.advanceToStage("Save steps")

            // then
            val state = engine.getActiveState()
            state!!.currentStage shouldBe CommandStage.DONE
            state.currentStep shouldBe 1
            state.expectedAction shouldBe "Save steps"
        }

        "from DONE throws exception" {
            // given
            engine.startCommand("plan", "Check task")
            engine.advanceToStage("Generate steps")
            engine.advanceToStage("Validate steps")
            engine.advanceToStage("Save steps")

            // when & then
            val exception = shouldThrow<IllegalStateException> {
                engine.advanceToStage("Next action")
            }
            exception.message shouldContain "Cannot advance from DONE stage"
        }

        "with explicit stage works" {
            // given
            engine.startCommand("plan", "Check task")

            // when
            engine.advanceToStage(CommandStage.EXECUTION, "Generate steps")

            // then
            val state = engine.getActiveState()
            state!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "without active command throws exception" {
            // when & then
            val exception = shouldThrow<IllegalStateException> {
                engine.advanceToStage("action")
            }
            exception.message shouldContain "No active command"
        }
    }

    "advanceStep" - {
        "increments step counter" {
            // given
            engine.startCommand("plan", "Step 1")

            // when
            engine.advanceStep("Step 2")

            // then
            val state = engine.getActiveState()
            state!!.currentStep shouldBe 2
            state.expectedAction shouldBe "Step 2"
        }

        "multiple times increments correctly" {
            // given
            engine.startCommand("plan", "Step 1")

            // when
            engine.advanceStep("Step 2")
            engine.advanceStep("Step 3")
            engine.advanceStep("Step 4")

            // then
            val state = engine.getActiveState()
            state!!.currentStep shouldBe 4
            state.expectedAction shouldBe "Step 4"
        }

        "without active command throws exception" {
            // when & then
            val exception = shouldThrow<IllegalStateException> {
                engine.advanceStep("action")
            }
            exception.message shouldContain "No active command"
        }
    }

    "context operations" - {
        "putContext and getContext work correctly" {
            // given
            engine.startCommand("plan", "action")

            // when
            engine.putContext("taskId", "123")
            engine.putContext("description", "Test task")

            // then
            engine.getContext("taskId") shouldBe "123"
            engine.getContext("description") shouldBe "Test task"
        }

        "getContext returns null for non-existent key" {
            // given
            engine.startCommand("plan", "action")

            // then
            engine.getContext("nonExistentKey").shouldBeNull()
        }

        "putContext overwrites existing value" {
            // given
            engine.startCommand("plan", "action")
            engine.putContext("key", "value1")

            // when
            engine.putContext("key", "value2")

            // then
            engine.getContext("key") shouldBe "value2"
        }

        "context preserved across stage transitions" {
            // given
            engine.startCommand("plan", "action")
            engine.putContext("taskId", "123")

            // when
            engine.advanceToStage("next stage")

            // then
            engine.getContext("taskId") shouldBe "123"
        }

        "context preserved across step transitions" {
            // given
            engine.startCommand("plan", "action")
            engine.putContext("taskId", "123")

            // when
            engine.advanceStep("next step")

            // then
            engine.getContext("taskId") shouldBe "123"
        }

        "putContext without active command throws exception" {
            // when & then
            val exception = shouldThrow<IllegalStateException> {
                engine.putContext("key", "value")
            }
            exception.message shouldContain "No active command"
        }

        "getContext without active command throws exception" {
            // when & then
            val exception = shouldThrow<IllegalStateException> {
                engine.getContext("key")
            }
            exception.message shouldContain "No active command"
        }
    }

    "completeCommand" - {
        "destroys state" {
            // given
            engine.startCommand("plan", "action")

            // when
            engine.completeCommand()

            // then
            engine.hasActiveCommand() shouldBe false
            engine.getActiveState().shouldBeNull()
        }

        "from non-DONE stage transitions to DONE first" {
            // given
            engine.startCommand("plan", "action")
            engine.advanceToStage("next stage")

            // when
            engine.completeCommand()

            // then
            engine.hasActiveCommand() shouldBe false
        }

        "without active command throws exception" {
            // when & then
            val exception = shouldThrow<IllegalStateException> {
                engine.completeCommand()
            }
            exception.message shouldContain "No active command"
        }
    }

    "abortCommand" - {
        "destroys state immediately" {
            // given
            engine.startCommand("plan", "action")
            engine.advanceToStage("next stage")
            engine.putContext("key", "value")

            // when
            engine.abortCommand()

            // then
            engine.hasActiveCommand() shouldBe false
            engine.getActiveState().shouldBeNull()
        }

        "without active command throws exception" {
            // when & then
            val exception = shouldThrow<IllegalStateException> {
                engine.abortCommand()
            }
            exception.message shouldContain "No active command"
        }
    }

    "full lifecycle" - {
        "full command lifecycle works correctly" {
            // given - запуск команды
            engine.startCommand("plan", "Check task")
            engine.hasActiveCommand() shouldBe true

            // when - PLANNING этап
            engine.putContext("taskId", "123")
            engine.advanceStep("Request description")

            // then
            engine.getActiveState()!!.currentStep shouldBe 2

            // when - переход к EXECUTION
            engine.advanceToStage("Generate steps")

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
            engine.getActiveState()!!.currentStep shouldBe 1

            // when - EXECUTION этап
            engine.putContext("steps", "Step 1, Step 2, Step 3")
            engine.advanceStep("Parse LLM response")

            // then
            engine.getActiveState()!!.currentStep shouldBe 2

            // when - переход к VALIDATION
            engine.advanceToStage("Validate steps")

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION

            // when - VALIDATION этап
            engine.advanceStep("Show steps to user")
            engine.advanceStep("Wait for confirmation")

            // then
            engine.getActiveState()!!.currentStep shouldBe 3

            // when - завершение команды
            engine.completeCommand()

            // then
            engine.hasActiveCommand() shouldBe false
            engine.getActiveState().shouldBeNull()
        }

        "can start new command after completeCommand" {
            // given
            engine.startCommand("plan", "action1")
            engine.completeCommand()

            // when
            engine.startCommand("describe", "action2")

            // then
            engine.hasActiveCommand() shouldBe true
            engine.getActiveState()!!.commandName shouldBe "describe"
        }

        "can start new command after abortCommand" {
            // given
            engine.startCommand("plan", "action1")
            engine.abortCommand()

            // when
            engine.startCommand("describe", "action2")

            // then
            engine.hasActiveCommand() shouldBe true
            engine.getActiveState()!!.commandName shouldBe "describe"
        }
    }
})
