package io.averkhogliad.ai.challenge.week3.cli.integration

import io.averkhogliad.ai.challenge.week3.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week3.cli.domain.model.StateMap
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TransitionNotAllowedException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Интеграционные тесты для команды :goto и сценариев отката/продолжения.
 *
 * US-TEST-3: Integration-тесты для команды :goto
 * US-TEST-4: Тестирование сценариев отката и продолжения
 *
 * Контекст: PlanCommandFsmIntegrationTest (существующий) + новый GotoCommandIntegrationTest.
 */
class GotoCommandIntegrationTest {

    private lateinit var engine: DefaultCommandEngine

    @BeforeEach
    fun setUp() {
        engine = DefaultCommandEngine()
    }

    // =====================================================
    // US-TEST-3: Integration-тесты для команды :goto
    // =====================================================

    // --- :goto без аргументов (buildStateMap) ---

    @Test
    fun `buildStateMap returns correct map from PLANNING with description`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("description", "Some description")

        val map: StateMap = engine.buildStateMap()

        assertEquals(CommandStage.PLANNING, map.currentState)
        assertEquals(5, map.states.size)
        assertTrue(map.availableTransitions.isNotEmpty())
    }

    @Test
    fun `buildStateMap returns correct map from EXECUTION with steps`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)

        val map: StateMap = engine.buildStateMap()

        assertEquals(CommandStage.EXECUTION, map.currentState)
        assertTrue(map.availableTransitions.size >= 2)
    }

    @Test
    fun `buildStateMap shows no available transitions from TERMINATED`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.DONE)
        engine.performTransition(CommandStage.TERMINATED)

        val map: StateMap = engine.buildStateMap()

        assertEquals(CommandStage.TERMINATED, map.currentState)
        assertTrue(map.availableTransitions.isEmpty())
    }

    // --- :goto <state> с допустимыми переходами ---

    @Test
    fun `goto PLANNING to EXECUTION succeeds with description`() {
        engine.startCommand("plan", "Test plan")

        engine.performTransition(CommandStage.EXECUTION)

        assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)
    }

    @Test
    fun `goto EXECUTION to VALIDATION succeeds with steps`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)

        assertEquals(CommandStage.VALIDATION, engine.getActiveState()!!.currentStage)
    }

    @Test
    fun `goto EXECUTION to PLANNING succeeds (rollback)`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.PLANNING)

        assertEquals(CommandStage.PLANNING, engine.getActiveState()!!.currentStage)
    }

    @Test
    fun `goto VALIDATION to DONE succeeds with steps`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.DONE)

        assertEquals(CommandStage.DONE, engine.getActiveState()!!.currentStage)
    }

    @Test
    fun `goto DONE to TERMINATED succeeds`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.DONE)
        engine.performTransition(CommandStage.TERMINATED)

        assertEquals(CommandStage.TERMINATED, engine.getActiveState()!!.currentStage)
        assertTrue(engine.getActiveState()!!.isTerminated())
    }

    @Test
    fun `goto VALIDATION to EXECUTION succeeds (edit)`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.EXECUTION)

        assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)
    }

    // --- :goto <state> с недопустимыми переходами ---

    @Test
    fun `goto PLANNING to EXECUTION fails without description`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("needsDescription", "true")

        assertThrows(TransitionNotAllowedException::class.java) {
            engine.performTransition(CommandStage.EXECUTION)
        }
    }

    @Test
    fun `goto EXECUTION to VALIDATION fails without steps`() {
        engine.startCommand("plan", "Test plan")
        engine.performTransition(CommandStage.EXECUTION)

        assertThrows(TransitionNotAllowedException::class.java) {
            engine.performTransition(CommandStage.VALIDATION)
        }
    }

    @Test
    fun `goto PLANNING to DONE fails (skip stages)`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

        assertThrows(TransitionNotAllowedException::class.java) {
            engine.performTransition(CommandStage.DONE)
        }
    }

    @Test
    fun `goto EXECUTION to DONE fails (skip VALIDATION)`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)

        assertThrows(TransitionNotAllowedException::class.java) {
            engine.performTransition(CommandStage.DONE)
        }
    }

    @Test
    fun `goto from TERMINATED fails for any target`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.performTransition(CommandStage.EXECUTION)
        engine.performTransition(CommandStage.VALIDATION)
        engine.performTransition(CommandStage.DONE)
        engine.performTransition(CommandStage.TERMINATED)

        val blocked = listOf(CommandStage.PLANNING, CommandStage.EXECUTION, CommandStage.VALIDATION, CommandStage.DONE)
        blocked.forEach { target ->
            assertThrows(TransitionNotAllowedException::class.java) {
                engine.performTransition(target)
            }
        }
    }

    @Test
    fun `goto to same state fails`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

        assertThrows(TransitionNotAllowedException::class.java) {
            engine.performTransition(CommandStage.PLANNING)
        }
    }

    // --- Проверка паузы ---

    @Test
    fun `goto works during pause`() {
        engine.startCommand("plan", "Test plan")
        engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
        engine.pause()

        engine.performTransition(CommandStage.EXECUTION)
        assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)
    }

    // =====================================================
    // US-TEST-4: Тестирование сценариев отката и продолжения
    // =====================================================

    @Nested
    inner class RollbackAndResume {

        @Test
        fun `rollback from EXECUTION to PLANNING preserves context`() {
            engine.startCommand("plan", "Test plan")
            engine.putContext("description", "My task description")
            engine.putContext("taskId", "task-42")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.putContext("executionError", "LLM error occurred")

            engine.performTransition(CommandStage.PLANNING)

            assertEquals(CommandStage.PLANNING, engine.getActiveState()!!.currentStage)
            assertEquals("My task description", engine.getContext("description"))
            assertEquals("task-42", engine.getContext("taskId"))
            assertEquals("LLM error occurred", engine.getContext("executionError"))
        }

        @Test
        fun `rollback from EXECUTION to PLANNING allows re-execution`() {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            engine.performTransition(CommandStage.PLANNING)
            assertEquals(CommandStage.PLANNING, engine.getActiveState()!!.currentStage)

            engine.performTransition(CommandStage.EXECUTION)
            assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)
        }

        @Test
        fun `transition history records rollback`() {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.PLANNING)

            val history = engine.getActiveState()!!.transitionHistory
            assertTrue(history.size >= 2)
            assertEquals(CommandStage.PLANNING, history[0].from)
            assertEquals(CommandStage.EXECUTION, history[0].to)
            assertEquals(CommandStage.EXECUTION, history[1].from)
            assertEquals(CommandStage.PLANNING, history[1].to)
        }

        @Test
        fun `resume after pause continues normally`() {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.pause()

            engine.resume()

            engine.performTransition(CommandStage.EXECUTION)
            assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)
        }

        @Test
        fun `resume after pause with changed context still allows transitions`() {
            engine.startCommand("plan", "Test plan")
            engine.pause()

            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.resume()

            engine.performTransition(CommandStage.EXECUTION)
            assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)
        }

        @Test
        fun `resume after pause when command terminated throws exception`() {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)
            engine.performTransition(CommandStage.VALIDATION)
            engine.performTransition(CommandStage.DONE)
            engine.performTransition(CommandStage.TERMINATED)

            assertTrue(engine.getActiveState()!!.isTerminated())

            assertThrows(IllegalStateException::class.java) {
                engine.resume()
            }
        }

        @Test
        fun `can always abort regardless of current state`() {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")
            engine.performTransition(CommandStage.EXECUTION)

            engine.abortCommand()

            assertFalse(engine.hasActiveCommand())
            assertNull(engine.getActiveState())
        }

        @Test
        fun `full happy path PLANNING to TERMINATED with all transitions`() {
            engine.startCommand("plan", "Test plan")
            engine.putContext("generatedSteps", """[{"title":"Step 1"}]""")

            engine.performTransition(CommandStage.EXECUTION)
            assertEquals(CommandStage.EXECUTION, engine.getActiveState()!!.currentStage)

            engine.performTransition(CommandStage.VALIDATION)
            assertEquals(CommandStage.VALIDATION, engine.getActiveState()!!.currentStage)

            engine.performTransition(CommandStage.DONE)
            assertEquals(CommandStage.DONE, engine.getActiveState()!!.currentStage)

            engine.performTransition(CommandStage.TERMINATED)
            assertEquals(CommandStage.TERMINATED, engine.getActiveState()!!.currentStage)
            assertTrue(engine.getActiveState()!!.isTerminated())

            val history = engine.getActiveState()!!.transitionHistory
            assertEquals(4, history.size)
        }
    }
}
