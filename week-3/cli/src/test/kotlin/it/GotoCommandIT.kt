package io.averkhogliad.ai.challenge.week3.cli.it

import io.averkhogliad.ai.challenge.week3.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week3.cli.domain.model.StateMap
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TransitionNotAllowedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Интеграционные тесты для команды :goto и сценариев отката/продолжения.
 *
 * US-TEST-3: Integration-тесты для команды :goto
 * US-TEST-4: Тестирование сценариев отката и продолжения
 *
 * Контекст: PlanCommandFsmIT (существующий) + новый GotoCommandIT.
 */
class GotoCommandIT : FreeSpec({

    lateinit var engine: DefaultCommandEngine

    beforeTest {
        engine = DefaultCommandEngine()
    }

    // =====================================================
    // US-TEST-3: Integration-тесты для команды :goto
    // =====================================================

    // --- :goto без аргументов (buildStateMap) ---

    "buildStateMap returns correct map from PLANNING with description" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("description", "Some description")

        val map: StateMap = engine.buildStateMap()

        map.currentState shouldBe CommandStage.PLANNING
        map.states.size shouldBe 5
        map.availableTransitions.isNotEmpty() shouldBe true
    }

    "buildStateMap returns correct map from EXECUTION with steps" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)

        val map: StateMap = engine.buildStateMap()

        map.currentState shouldBe CommandStage.EXECUTION
        (map.availableTransitions.size >= 2) shouldBe true
    }

    "buildStateMap shows no available transitions from TERMINATED" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.DONE)
        engine.performTransition(CommandStage.TERMINATED)

        val map: StateMap = engine.buildStateMap()

        map.currentState shouldBe CommandStage.TERMINATED
        map.availableTransitions.isEmpty() shouldBe true
    }

    // --- :goto <state> с допустимыми переходами ---

    "goto PLANNING to EXECUTION succeeds with description" {
        engine.startCommand("plan", "Test plan")

        engine.performTransition(CommandStage.EXECUTION)

        engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
    }

    "goto EXECUTION to VALIDATION succeeds with steps" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)

        engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION
    }

    "goto EXECUTION to PLANNING succeeds (rollback)" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.PLANNING)

        engine.getActiveState()!!.currentStage shouldBe CommandStage.PLANNING
    }

    "goto VALIDATION to DONE succeeds with steps" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.DONE)

        engine.getActiveState()!!.currentStage shouldBe CommandStage.DONE
    }

    "goto DONE to TERMINATED succeeds" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.DONE)
        engine.performTransition(CommandStage.TERMINATED)

        engine.getActiveState()!!.currentStage shouldBe CommandStage.TERMINATED
        engine.getActiveState()!!.isTerminated() shouldBe true
    }

    "goto VALIDATION to EXECUTION succeeds (edit)" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.EXECUTION)

        engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
    }

    // --- :goto <state> с недопустимыми переходами ---

    "goto PLANNING to EXECUTION fails without description" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("needsDescription", "true")

        shouldThrow<TransitionNotAllowedException> {
            engine.performTransition(CommandStage.EXECUTION)
        }
    }

    "goto EXECUTION to VALIDATION fails without steps" {
        engine.startCommand("plan", "Test plan")
        engine.performTransition(CommandStage.EXECUTION)

        shouldThrow<TransitionNotAllowedException> {
            engine.performTransition(CommandStage.VALIDATION)
        }
    }

    "goto PLANNING to DONE fails (skip stages)" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

        shouldThrow<TransitionNotAllowedException> {
            engine.performTransition(CommandStage.DONE)
        }
    }

    "goto EXECUTION to DONE fails (skip VALIDATION)" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)

        shouldThrow<TransitionNotAllowedException> {
            engine.performTransition(CommandStage.DONE)
        }
    }

    "goto from TERMINATED fails for any target" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.DONE)
        engine.performTransition(CommandStage.TERMINATED)

        val blocked = listOf(CommandStage.PLANNING, CommandStage.EXECUTION, CommandStage.VALIDATION, CommandStage.DONE)
        blocked.forEach { target ->
            shouldThrow<TransitionNotAllowedException> {
                engine.performTransition(target)
            }
        }
    }

    "goto to same state fails" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

        shouldThrow<TransitionNotAllowedException> {
            engine.performTransition(CommandStage.PLANNING)
        }
    }

    // --- Проверка паузы ---

    "goto works during pause" {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.pause()

        engine.performTransition(CommandStage.EXECUTION)
        engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
    }

    // =====================================================
    // US-TEST-4: Тестирование сценариев отката и продолжения
    // =====================================================

    "Rollback and Resume" - {
        "rollback from EXECUTION to PLANNING preserves context" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("description", "My task description")
            engine.putContext("taskId", "task-42")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.putContext("executionError", "LLM error occurred")

            engine.performTransition(CommandStage.PLANNING)

            engine.getActiveState()!!.currentStage shouldBe CommandStage.PLANNING
            engine.getContext("description") shouldBe "My task description"
            engine.getContext("taskId") shouldBe "task-42"
            engine.getContext("executionError") shouldBe "LLM error occurred"
        }

        "rollback from EXECUTION to PLANNING allows re-execution" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            engine.performTransition(CommandStage.PLANNING)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.PLANNING

            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "transition history records rollback" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.PLANNING)

            val history = engine.getActiveState()!!.transitionHistory
            (history.size >= 2) shouldBe true
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
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "resume after pause with changed context still allows transitions" {
            engine.startCommand("plan", "Test plan")
            engine.pause()

            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.resume()

            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION
        }

        "resume after pause when command terminated throws exception" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)
            engine.performTransition(CommandStage.TERMINATED)

            engine.getActiveState()!!.isTerminated() shouldBe true

            shouldThrow<IllegalStateException> {
                engine.resume()
            }
        }

        "can always abort regardless of current state" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            engine.abortCommand()

            engine.hasActiveCommand() shouldBe false
            engine.getActiveState() shouldBe null
        }

        "full happy path PLANNING to TERMINATED with all transitions" {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

            engine.performTransition(CommandStage.EXECUTION)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.EXECUTION

            engine.performTransition(CommandStage.VALIDATION)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.VALIDATION

            engine.performTransition(CommandStage.DONE)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.DONE

            engine.performTransition(CommandStage.TERMINATED)
            engine.getActiveState()!!.currentStage shouldBe CommandStage.TERMINATED
            engine.getActiveState()!!.isTerminated() shouldBe true

            val history = engine.getActiveState()!!.transitionHistory
            history.size shouldBe 4
        }
    }
})
