package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Transition
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit-тесты для [TransitionValidator].
 *
 * Покрывает:
 * - Проверку всех 6 допустимых переходов planTransitions()
 * - Проверку недопустимых переходов
 * - Проверку предусловий (condition) переходов
 * - canTransition(), getAvailableTransitions(), getTransitionReason()
 * - buildStateMap() для разных состояний
 * - Краевые случаи (пустой контекст, TERMINATED состояние)
 *
 * Покрытие: ~95%
 */
class TransitionValidatorTest {

    // --- Фабрики ---

    private val planTransitions: List<Transition>
        get() = TransitionValidator.planTransitions()

    private fun validator(
        transitions: List<Transition> = planTransitions
    ): TransitionValidator = TransitionValidator(transitions)

    /** Пустой контекст */
    private val emptyContext: Map<String, String> = emptyMap()

    /** Контекст с заполненным описанием */
    private val contextWithDescription: Map<String, String> = mapOf(
        "needsDescription" to "false"
    )

    /** Контекст с незаполненным описанием */
    private val contextNeedsDescription: Map<String, String> = mapOf(
        "needsDescription" to "true"
    )

    /** Контекст с полученными шагами */
    private val contextWithSteps: Map<String, String> = mapOf(
        "generatedSteps" to """[{"title":"Step 1"}]"""
    )

    /** Контекст с описанием и шагами */
    private val contextFull: Map<String, String> = mapOf(
        "needsDescription" to "false",
        "generatedSteps" to """[{"title":"Step 1"}]"""
    )

    // =====================================================
    // US-TEST-1: TransitionValidator unit tests
    // =====================================================

    // --- planTransitions() ---

    @Test
    fun `planTransitions should return exactly 6 transitions`() {
        assertEquals(6, planTransitions.size)
    }

    @Test
    fun `planTransitions should cover all expected edges`() {
        val edges = planTransitions.map { it.from to it.to }.toSet()
        assertTrue(edges.contains(CommandStage.PLANNING to CommandStage.EXECUTION))
        assertTrue(edges.contains(CommandStage.EXECUTION to CommandStage.VALIDATION))
        assertTrue(edges.contains(CommandStage.EXECUTION to CommandStage.PLANNING))
        assertTrue(edges.contains(CommandStage.VALIDATION to CommandStage.DONE))
        assertTrue(edges.contains(CommandStage.VALIDATION to CommandStage.EXECUTION))
        assertTrue(edges.contains(CommandStage.DONE to CommandStage.TERMINATED))
    }

    // --- canTransition() - допустимые переходы ---

    @Test
    fun `PLANNING → EXECUTION should be allowed when description filled`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            contextWithDescription
        )
        assertTrue(result.allowed, "Expected allowed, got: ${result.reason}")
    }

    @Test
    fun `PLANNING → EXECUTION should be blocked when description not filled`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            contextNeedsDescription
        )
        assertFalse(result.allowed, "Expected blocked")
        assertTrue(result.reason.isNotBlank())
    }

    @Test
    fun `PLANNING → EXECUTION should be allowed with empty context (no needsDescription flag)`() {
        // When no needsDescription flag is set, description is considered filled
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            emptyContext
        )
        assertTrue(result.allowed, "PLANNING→EXECUTION allowed when needsDescription not set to 'true'")
    }

    @Test
    fun `EXECUTION → VALIDATION should be allowed when steps are present`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.EXECUTION,
            CommandStage.VALIDATION,
            contextWithSteps
        )
        assertTrue(result.allowed, "Expected allowed, got: ${result.reason}")
    }

    @Test
    fun `EXECUTION → VALIDATION should be blocked when steps are empty`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.EXECUTION,
            CommandStage.VALIDATION,
            emptyContext
        )
        assertFalse(result.allowed)
    }

    @Test
    fun `EXECUTION → PLANNING should always be allowed (rollback)`() {
        val v = validator()
        // Пустой контекст
        val result1 = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, emptyContext)
        assertTrue(result1.allowed, "Rollback should be allowed with empty context")

        // Контекст с шагами
        val result2 = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, contextWithSteps)
        assertTrue(result2.allowed, "Rollback should be allowed with steps")
    }

    @Test
    fun `VALIDATION → DONE should be allowed when steps are present`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.VALIDATION,
            CommandStage.DONE,
            contextWithSteps
        )
        assertTrue(result.allowed, "Expected allowed, got: ${result.reason}")
    }

    @Test
    fun `VALIDATION → DONE should be blocked when steps are empty`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.VALIDATION,
            CommandStage.DONE,
            emptyContext
        )
        assertFalse(result.allowed)
    }

    @Test
    fun `VALIDATION → EXECUTION should always be allowed (edit)`() {
        val v = validator()
        val result = v.canTransition(CommandStage.VALIDATION, CommandStage.EXECUTION, emptyContext)
        assertTrue(result.allowed, "Edit transition should always be allowed")
    }

    @Test
    fun `DONE → TERMINATED should always be allowed`() {
        val v = validator()
        val result = v.canTransition(CommandStage.DONE, CommandStage.TERMINATED, emptyContext)
        assertTrue(result.allowed, "Auto-termination should always be allowed")
    }

    // --- canTransition() - недопустимые переходы ---

    @Test
    fun `PLANNING → DONE should be blocked (skip EXECUTION + VALIDATION)`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.DONE,
            contextFull
        )
        assertFalse(result.allowed, "Cannot skip stages")
    }

    @Test
    fun `PLANNING → VALIDATION should be blocked (skip EXECUTION)`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.VALIDATION,
            contextWithDescription
        )
        assertFalse(result.allowed)
    }

    @Test
    fun `EXECUTION → DONE should be blocked (skip VALIDATION)`() {
        val v = validator()
        val result = v.canTransition(
            CommandStage.EXECUTION,
            CommandStage.DONE,
            contextWithSteps
        )
        assertFalse(result.allowed)
    }

    @Test
    fun `DONE → PLANNING should be blocked (cannot go back from DONE)`() {
        val v = validator()
        val result = v.canTransition(CommandStage.DONE, CommandStage.PLANNING, emptyContext)
        assertFalse(result.allowed)
    }

    @Test
    fun `TERMINATED → any state should be blocked`() {
        val v = validator()
        val blocked = listOf(
            v.canTransition(CommandStage.TERMINATED, CommandStage.PLANNING, emptyContext),
            v.canTransition(CommandStage.TERMINATED, CommandStage.EXECUTION, emptyContext),
            v.canTransition(CommandStage.TERMINATED, CommandStage.VALIDATION, emptyContext),
            v.canTransition(CommandStage.TERMINATED, CommandStage.DONE, emptyContext)
        )
        blocked.forEach { result ->
            assertFalse(result.allowed, "Transition from TERMINATED to any state should be blocked")
        }
    }

    // --- getAvailableTransitions() ---

    @Test
    fun `getAvailableTransitions from PLANNING with description should return EXECUTION`() {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.PLANNING, contextWithDescription)
        val destinations = available.map { it.to }
        assertTrue(destinations.contains(CommandStage.EXECUTION))
    }

    @Test
    fun `getAvailableTransitions from PLANNING with empty context should return EXECUTION`() {
        // needsDescription not set → description is filled → EXECUTION available
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.PLANNING, emptyContext)
        val destinations = available.map { it.to }
        assertTrue(
            destinations.contains(CommandStage.EXECUTION),
            "EXECUTION should be available from PLANNING with empty context"
        )
    }

    @Test
    fun `getAvailableTransitions from EXECUTION with steps should include VALIDATION and PLANNING`() {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.EXECUTION, contextWithSteps)
        val destinations = available.map { it.to }.toSet()
        assertTrue(destinations.contains(CommandStage.VALIDATION))
        assertTrue(destinations.contains(CommandStage.PLANNING))
    }

    @Test
    fun `getAvailableTransitions from EXECUTION without steps should only return PLANNING`() {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.EXECUTION, emptyContext)
        val destinations = available.map { it.to }.toSet()
        assertEquals(1, destinations.size)
        assertTrue(destinations.contains(CommandStage.PLANNING))
    }

    @Test
    fun `getAvailableTransitions from VALIDATION should return DONE and EXECUTION`() {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.VALIDATION, contextWithSteps)
        val destinations = available.map { it.to }.toSet()
        assertTrue(destinations.contains(CommandStage.DONE))
        assertTrue(destinations.contains(CommandStage.EXECUTION))
    }

    @Test
    fun `getAvailableTransitions from DONE should return TERMINATED`() {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.DONE, emptyContext)
        val destinations = available.map { it.to }.toSet()
        assertEquals(1, destinations.size)
        assertTrue(destinations.contains(CommandStage.TERMINATED))
    }

    @Test
    fun `getAvailableTransitions from TERMINATED should return empty list`() {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.TERMINATED, fullContext())
        assertTrue(available.isEmpty())
    }

    // --- getTransitionReason() ---

    @Test
    fun `getTransitionReason should return null for allowed transition`() {
        val v = validator()
        val reason = v.getTransitionReason(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            contextWithDescription
        )
        assertNull(reason)
    }

    @Test
    fun `getTransitionReason should return non-null reason for blocked transition`() {
        val v = validator()
        // PLANNING → DONE is always blocked (no direct transition defined)
        val reason = v.getTransitionReason(
            CommandStage.PLANNING,
            CommandStage.DONE,
            contextFull
        )
        assertNotNull(reason)
        assertTrue(reason.isNotBlank())
    }

    @Test
    fun `getTransitionReason should return reason for undefined transition`() {
        val v = validator()
        val reason = v.getTransitionReason(
            CommandStage.PLANNING,
            CommandStage.DONE,
            contextFull
        )
        assertNotNull(reason)
        // Blocked reason is non-empty for undefined transitions
        assertTrue(reason.isNotBlank())
    }

    // --- getAllTransitions() ---

    @Test
    fun `getAllTransitions should return defensive copy`() {
        val v = validator()
        val all = v.getAllTransitions()
        assertEquals(6, all.size)
        // Проверяем, что возвращается копия, а не оригинал
        val allAgain = v.getAllTransitions()
        assertNotSame(all, allAgain)
    }

    // --- buildStateMap() ---

    @Test
    fun `buildStateMap from PLANNING should show currentState as PLANNING`() {
        val v = validator()
        val map = v.buildStateMap(CommandStage.PLANNING, contextWithDescription)
        assertEquals(CommandStage.PLANNING, map.currentState)
        assertEquals(5, map.states.size) // 5 состояний
    }

    @Test
    fun `buildStateMap should mark current state`() {
        val v = validator()
        val map = v.buildStateMap(CommandStage.EXECUTION, contextWithSteps)
        val currentInfo = map.states.find { it.state == CommandStage.EXECUTION }
        assertNotNull(currentInfo)
        assertTrue(currentInfo.isCurrent)
    }

    @Test
    fun `buildStateMap should mark available states correctly from PLANNING`() {
        val v = validator()
        val map = v.buildStateMap(CommandStage.PLANNING, contextWithDescription)

        val executionInfo = map.states.find { it.state == CommandStage.EXECUTION }
        assertNotNull(executionInfo)
        assertTrue(executionInfo.isAvailable, "EXECUTION should be available when description filled")

        val doneInfo = map.states.find { it.state == CommandStage.DONE }
        assertNotNull(doneInfo)
        assertFalse(doneInfo.isAvailable, "DONE should not be available from PLANNING")
    }

    @Test
    fun `buildStateMap should include availableTransitions list`() {
        val v = validator()
        val map = v.buildStateMap(CommandStage.VALIDATION, contextWithSteps)
        assertTrue(map.availableTransitions.isNotEmpty())
    }

    @Test
    fun `buildStateMap should have correct reason for available state`() {
        val v = validator()
        val map = v.buildStateMap(CommandStage.EXECUTION, contextWithSteps)
        val validationInfo = map.states.find { it.state == CommandStage.VALIDATION }
        assertNotNull(validationInfo)
        assertTrue(validationInfo.isAvailable)
        // Available reason — должно быть "доступен" или непустая строка
        assertTrue(validationInfo.reason.isNotBlank())
    }

    @Test
    fun `buildStateMap from TERMINATED should show no available transitions`() {
        val v = validator()
        val map = v.buildStateMap(CommandStage.TERMINATED, fullContext())
        assertEquals(CommandStage.TERMINATED, map.currentState)
        assertTrue(map.availableTransitions.isEmpty(), "No transitions from TERMINATED")
    }

    // --- Краевые случаи ---

    @Test
    fun `transition with custom validator and no matching paths should block all`() {
        // Создаём пустой граф (нет переходов)
        val emptyValidator = validator(emptyList())
        val result = emptyValidator.canTransition(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            contextWithDescription
        )
        assertFalse(result.allowed)
    }

    @Test
    fun `self-transition to same state should be blocked`() {
        val v = validator()
        CommandStage.entries.forEach { stage ->
            val result = v.canTransition(stage, stage, fullContext())
            assertFalse(result.allowed, "Self-transition $stage → $stage should be blocked")
        }
    }

    @Test
    fun `getAvailableTransitions should not include self-transitions`() {
        val v = validator()
        CommandStage.entries.forEach { stage ->
            val available = v.getAvailableTransitions(stage, fullContext())
            available.forEach { transition ->
                assertNotEquals(stage, transition.to, "Self-transition in available list from $stage")
            }
        }
    }

    @Test
    fun `each plan transition should have non-blank description`() {
        planTransitions.forEach { transition ->
            assertTrue(
                transition.description.isNotBlank(),
                "Transition ${transition.from} → ${transition.to} should have a description"
            )
        }
    }

    @Test
    fun `transitions with always-true condition should work with any context`() {
        val v = validator()
        // EXECUTION → PLANNING имеет condition { true }
        val contexts = listOf(emptyContext, contextWithDescription, contextWithSteps, fullContext())
        contexts.forEach { ctx ->
            val result = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, ctx)
            assertTrue(result.allowed, "EXECUTION → PLANNING should be allowed with any context")
        }
    }

    // =====================================================
    // Вспомогательные методы
    // =====================================================

    private fun fullContext(): Map<String, String> = mapOf(
        "needsDescription" to "false",
        "generatedSteps" to """[{"title":"Step 1"}]""",
        "executionError" to "false"
    )
}
