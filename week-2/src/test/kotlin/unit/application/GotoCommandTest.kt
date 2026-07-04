package io.averkhogliad.ai.challenge.week2.unit.application

import io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week2.domain.model.TransitionNotAllowedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Тесты для команды :goto и сценариев отката/продолжения.
 *
 * US-TEST-3: Тесты для команды :goto
 * US-TEST-4: Тестирование сценариев отката и продолжения
 */
class GotoCommandTest : FreeSpec({

    lateinit var engine: DefaultCommandEngine

    beforeEach {
        engine = DefaultCommandEngine()
    }

    // =====================================================
    // US-TEST-3: Тесты для команды :goto
    // =====================================================

    "buildStateMap" - {

        "returns correct map from PLANNING with description" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("description", "Some description")

            val map = engine.buildStateMap()

            map.currentState shouldBe CommandStage.PLANNING
            map.states.size shouldBe 5
            map.availableTransitions.shouldNotBeEmpty()
        }

        "returns correct map from EXECUTION with steps" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            val map = engine.buildStateMap()

            map.currentState shouldBe CommandStage.EXECUTION
            map.availableTransitions.size shouldBeGreaterThanOrEqual 2
        }

        "shows no available transitions from TERMINATED" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)
            engine.performTransition(CommandStage.TERMINATED)

            val map = engine.buildStateMap()

            map.currentState shouldBe CommandStage.TERMINATED
            map.availableTransitions.shouldBeEmpty()
        }
    }

    "performTransition valid" - {

        "PLANNING to EXECUTION succeeds with description" {
            engine.startCommand("plan", "Test plan")

            engine.performTransition(CommandStage.EXECUTION)

            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION
        }

        "EXECUTION to VALIDATION succeeds with steps" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            engine.performTransition(CommandStage.VALIDATION)

            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.VALIDATION
        }

        "EXECUTION to PLANNING succeeds (rollback)" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            engine.performTransition(CommandStage.PLANNING)

            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.PLANNING
        }

        "VALIDATION to DONE succeeds with steps" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)

            engine.performTransition(CommandStage.DONE)

            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.DONE
        }

        "DONE to TERMINATED succeeds" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)

            engine.performTransition(CommandStage.TERMINATED)

            val state = engine.getActiveState().shouldNotBeNull()
            state.currentStage shouldBe CommandStage.TERMINATED
            state.isTerminated().shouldBeTrue()
        }

        "VALIDATION to EXECUTION succeeds (edit)" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)

            engine.performTransition(CommandStage.EXECUTION)

            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION
        }

        "works during pause" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.pause()

            engine.performTransition(CommandStage.EXECUTION)

            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION
        }
    }

    "performTransition invalid" - {

        "PLANNING to EXECUTION fails without description" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("needsDescription", "true")

            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.EXECUTION)
            }
        }

        "EXECUTION to VALIDATION fails without steps" {
            engine.startCommand("plan", "Test plan")
            engine.performTransition(CommandStage.EXECUTION)

            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.VALIDATION)
            }
        }

        "PLANNING to DONE fails (skip stages)" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.DONE)
            }
        }

        "EXECUTION to DONE fails (skip VALIDATION)" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.DONE)
            }
        }

        "from TERMINATED fails for any target" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)
            engine.performTransition(CommandStage.TERMINATED)

            val blocked =
                listOf(CommandStage.PLANNING, CommandStage.EXECUTION, CommandStage.VALIDATION, CommandStage.DONE)
            blocked.forEach { target ->
                shouldThrow<TransitionNotAllowedException> {
                    engine.performTransition(target)
                }
            }
        }

        "to same state fails" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(CommandStage.PLANNING)
            }
        }
    }

    // =====================================================
    // US-TEST-4: Тестирование сценариев отката и продолжения
    // =====================================================

    "rollback and resume" - {

        "rollback from EXECUTION to PLANNING preserves context" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("description", "My task description")
            engine.putContext("taskId", "task-42")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.putContext("executionError", "LLM error occurred")

            engine.performTransition(CommandStage.PLANNING)

            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.PLANNING
            engine.getContext("description") shouldBe "My task description"
            engine.getContext("taskId") shouldBe "task-42"
            engine.getContext("executionError") shouldBe "LLM error occurred"
        }

        "rollback from EXECUTION to PLANNING allows re-execution" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            engine.performTransition(CommandStage.PLANNING)
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.PLANNING

            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION
        }

        "transition history records rollback" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.PLANNING)

            val history = engine.getActiveState().shouldNotBeNull().transitionHistory
            history.size shouldBe 2
            history[0].from shouldBe CommandStage.PLANNING
            history[0].to shouldBe CommandStage.EXECUTION
            history[1].from shouldBe CommandStage.EXECUTION
            history[1].to shouldBe CommandStage.PLANNING
        }

        "resume after pause continues normally" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.pause()

            engine.resume()

            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION
        }

        "resume after pause with changed context still allows transitions" {
            engine.startCommand("plan", "Test plan")
            engine.pause()

            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.resume()

            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION
        }

        "resume after pause when command terminated throws exception" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)
            engine.performTransition(CommandStage.TERMINATED)

            engine.getActiveState().shouldNotBeNull().isTerminated().shouldBeTrue()

            shouldThrow<IllegalStateException> {
                engine.resume()
            }
        }

        "can always abort regardless of current state" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            engine.abortCommand()

            engine.hasActiveCommand().shouldBeFalse()
            engine.getActiveState().shouldBeNull()
        }

        "full happy path PLANNING to TERMINATED with all transitions" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.EXECUTION

            engine.performTransition(CommandStage.VALIDATION)
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.VALIDATION

            engine.performTransition(CommandStage.DONE)
            engine.getActiveState().shouldNotBeNull().currentStage shouldBe CommandStage.DONE

            engine.performTransition(CommandStage.TERMINATED)
            val state = engine.getActiveState().shouldNotBeNull()
            state.currentStage shouldBe CommandStage.TERMINATED
            state.isTerminated().shouldBeTrue()

            state.transitionHistory.size shouldBe 4
        }
    }
})
