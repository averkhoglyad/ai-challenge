package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week4.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.StateMap
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TransitionNotAllowedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Unit-тесты для команды :goto и сценариев отката/продолжения.
 *
 * US-TEST-3: Тесты для команды :goto
 * US-TEST-4: Тестирование сценариев отката и продолжения
 */
class GotoCommandTest : FreeSpec({

    lateinit var engine: DefaultCommandEngine

    beforeTest {
        engine = DefaultCommandEngine()
    }

    // =====================================================
    // US-TEST-3: Integration-тесты для команды :goto
    // =====================================================

    "buildStateMap" - {
        "returns correct map from PLANNING with description" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("description", "Some description")

            // when
            val map: StateMap = engine.buildStateMap()

            // then
            map.currentState shouldBe CommandStage.PLANNING
            map.states shouldHaveSize 5
            map.availableTransitions.shouldNotBeEmpty()
        }

        "returns correct map from EXECUTION with steps" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            // when
            val map: StateMap = engine.buildStateMap()

            // then
            map.currentState shouldBe CommandStage.EXECUTION
            (map.availableTransitions.size >= 2) shouldBe true
        }

        "shows no available transitions from TERMINATED" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)
            engine.performTransition(CommandStage.TERMINATED)

            // when
            val map: StateMap = engine.buildStateMap()

            // then
            map.currentState shouldBe CommandStage.TERMINATED
            map.availableTransitions.shouldHaveSize(0)
        }
    }

    "Valid goto transitions" - {
        "goto PLANNING to EXECUTION succeeds with description" {
            // given
            engine.startCommand("plan", "Test plan")

            // when
            engine.performTransition(CommandStage.EXECUTION)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "goto EXECUTION to VALIDATION succeeds with steps" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            // when
            engine.performTransition(CommandStage.VALIDATION)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION
        }

        "goto EXECUTION to PLANNING succeeds (rollback)" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            // when
            engine.performTransition(CommandStage.PLANNING)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.PLANNING
        }

        "goto VALIDATION to DONE succeeds with steps" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)

            // when
            engine.performTransition(CommandStage.DONE)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.DONE
        }

        "goto DONE to TERMINATED succeeds" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)

            // when
            engine.performTransition(CommandStage.TERMINATED)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.TERMINATED
            engine.getActiveState()!!.isTerminated() shouldBe true
        }

        "goto VALIDATION to EXECUTION succeeds (edit)" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)

            // when
            engine.performTransition(CommandStage.EXECUTION)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }
    }

    "Invalid goto transitions" - {
        "goto PLANNING to EXECUTION fails without description" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("needsDescription", "true")

            // when & then
            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.EXECUTION)
            }
        }

        "goto EXECUTION to VALIDATION fails without steps" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.performTransition(CommandStage.EXECUTION)

            // when & then
            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.VALIDATION)
            }
        }

        "goto PLANNING to DONE fails (skip stages)" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

            // when & then
            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.DONE)
            }
        }

        "goto EXECUTION to DONE fails (skip VALIDATION)" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            // when & then
            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.DONE)
            }
        }

        "goto from TERMINATED fails for any target" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)
            engine.performTransition(CommandStage.TERMINATED)

            // when & then
            val blocked =
                listOf(CommandStage.PLANNING, CommandStage.EXECUTION, CommandStage.VALIDATION, CommandStage.DONE)
            blocked.forEach { target ->
                shouldThrow<TransitionNotAllowedException> {
                    engine.performTransition(target)
                }
            }
        }

        "goto to same state fails" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

            // when & then
            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.PLANNING)
            }
        }
    }

    "Goto with pause" - {
        "goto works during pause" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.pause()

            // when
            engine.performTransition(CommandStage.EXECUTION)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }
    }

    // =====================================================
    // US-TEST-4: Тестирование сценариев отката и продолжения
    // =====================================================

    "RollbackAndResume" - {

        "rollback from EXECUTION to PLANNING preserves context" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("description", "My task description")
            engine.putContext("taskId", "task-42")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.putContext("executionError", "LLM error occurred")

            // when
            engine.performTransition(CommandStage.PLANNING)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.PLANNING
            engine.getContext("description") shouldBe "My task description"
            engine.getContext("taskId") shouldBe "task-42"
            engine.getContext("executionError") shouldBe "LLM error occurred"
        }

        "rollback from EXECUTION to PLANNING allows re-execution" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            // when
            engine.performTransition(CommandStage.PLANNING)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.PLANNING

            // when - re-execute
            engine.performTransition(CommandStage.EXECUTION)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "transition history records rollback" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.PLANNING)

            // when
            val history = engine.getActiveState()!!.transitionHistory

            // then
            (history.size >= 2) shouldBe true
            history[0].from shouldBe CommandStage.PLANNING
            history[0].to shouldBe CommandStage.EXECUTION
            history[1].from shouldBe CommandStage.EXECUTION
            history[1].to shouldBe CommandStage.PLANNING
        }

        "resume after pause continues normally" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.pause()

            // when
            engine.resume()

            // then
            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "resume after pause with changed context still allows transitions" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.pause()

            // when
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.resume()

            // then
            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "resume after pause when command terminated throws exception" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)
            engine.performTransition(CommandStage.TERMINATED)

            engine.getActiveState()!!.isTerminated() shouldBe true

            // when & then
            shouldThrow<IllegalStateException> {
                engine.resume()
            }
        }

        "can always abort regardless of current state" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            // when
            engine.abortCommand()

            // then
            engine.hasActiveCommand() shouldBe false
            engine.getActiveState() shouldBe null
        }

        "full happy path PLANNING to TERMINATED with all transitions" {
            // given
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

            // when
            engine.performTransition(CommandStage.EXECUTION)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION

            // when
            engine.performTransition(CommandStage.VALIDATION)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION

            // when
            engine.performTransition(CommandStage.DONE)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.DONE

            // when
            engine.performTransition(CommandStage.TERMINATED)

            // then
            engine.getActiveState()!!.currentStage shouldBe CommandStage.TERMINATED
            engine.getActiveState()!!.isTerminated() shouldBe true

            val history = engine.getActiveState()!!.transitionHistory
            history shouldHaveSize 4
        }
    }
})
