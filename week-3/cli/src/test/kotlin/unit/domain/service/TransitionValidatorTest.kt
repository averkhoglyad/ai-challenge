package io.averkhogliad.ai.challenge.week3.cli.unit.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.service.*

import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Transition
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

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
class TransitionValidatorTest : FreeSpec({

    // --- Фабрики ---

    fun planTransitions(): List<Transition> = TransitionValidator.planTransitions()

    fun validator(
        transitions: List<Transition> = planTransitions()
    ): TransitionValidator = TransitionValidator(transitions)

    /** Пустой контекст */
    val emptyContext: Map<String, String> = emptyMap()

    /** Контекст с заполненным описанием */
    val contextWithDescription: Map<String, String> = mapOf(
        "needsDescription" to "false"
    )

    /** Контекст с незаполненным описанием */
    val contextNeedsDescription: Map<String, String> = mapOf(
        "needsDescription" to "true"
    )

    /** Контекст с полученными шагами */
    val contextWithSteps: Map<String, String> = mapOf(
        "generatedSteps" to """[{"title":"Step 1"}]"""
    )

    /** Контекст с описанием и шагами */
    val contextFull: Map<String, String> = mapOf(
        "needsDescription" to "false",
        "generatedSteps" to """[{"title":"Step 1"}]"""
    )

    fun fullContext(): Map<String, String> = mapOf(
        "needsDescription" to "false",
        "generatedSteps" to """[{"title":"Step 1"}]""",
        "executionError" to "false"
    )

    // =====================================================
    // US-TEST-1: TransitionValidator unit tests
    // =====================================================

    // --- planTransitions() ---

    "planTransitions should return exactly 6 transitions" {
        planTransitions().size shouldBe 6
    }

    "planTransitions should cover all expected edges" {
        val edges = planTransitions().map { it.from to it.to }.toSet()
        edges.contains(CommandStage.PLANNING to CommandStage.EXECUTION) shouldBe true
        edges.contains(CommandStage.EXECUTION to CommandStage.VALIDATION) shouldBe true
        edges.contains(CommandStage.EXECUTION to CommandStage.PLANNING) shouldBe true
        edges.contains(CommandStage.VALIDATION to CommandStage.DONE) shouldBe true
        edges.contains(CommandStage.VALIDATION to CommandStage.EXECUTION) shouldBe true
        edges.contains(CommandStage.DONE to CommandStage.TERMINATED) shouldBe true
    }

    // --- canTransition() - допустимые переходы ---

    "PLANNING → EXECUTION should be allowed when description filled" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            contextWithDescription
        )
        result.allowed shouldBe true
    }

    "PLANNING → EXECUTION should be blocked when description not filled" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            contextNeedsDescription
        )
        result.allowed shouldBe false
        result.reason.isNotBlank() shouldBe true
    }

    "PLANNING → EXECUTION should be allowed with empty context (no needsDescription flag)" {
        // When no needsDescription flag is set, description is considered filled
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            emptyContext
        )
        result.allowed shouldBe true
    }

    "EXECUTION → VALIDATION should be allowed when steps are present" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.EXECUTION,
            CommandStage.VALIDATION,
            contextWithSteps
        )
        result.allowed shouldBe true
    }

    "EXECUTION → VALIDATION should be blocked when steps are empty" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.EXECUTION,
            CommandStage.VALIDATION,
            emptyContext
        )
        result.allowed shouldBe false
    }

    "EXECUTION → PLANNING should always be allowed (rollback)" {
        val v = validator()
        // Пустой контекст
        val result1 = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, emptyContext)
        result1.allowed shouldBe true

        // Контекст с шагами
        val result2 = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, contextWithSteps)
        result2.allowed shouldBe true
    }

    "VALIDATION → DONE should be allowed when steps are present" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.VALIDATION,
            CommandStage.DONE,
            contextWithSteps
        )
        result.allowed shouldBe true
    }

    "VALIDATION → DONE should be blocked when steps are empty" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.VALIDATION,
            CommandStage.DONE,
            emptyContext
        )
        result.allowed shouldBe false
    }

    "VALIDATION → EXECUTION should always be allowed (edit)" {
        val v = validator()
        val result = v.canTransition(CommandStage.VALIDATION, CommandStage.EXECUTION, emptyContext)
        result.allowed shouldBe true
    }

    "DONE → TERMINATED should always be allowed" {
        val v = validator()
        val result = v.canTransition(CommandStage.DONE, CommandStage.TERMINATED, emptyContext)
        result.allowed shouldBe true
    }

    // --- canTransition() - недопустимые переходы ---

    "PLANNING → DONE should be blocked (skip EXECUTION + VALIDATION)" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.DONE,
            contextFull
        )
        result.allowed shouldBe false
    }

    "PLANNING → VALIDATION should be blocked (skip EXECUTION)" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.PLANNING,
            CommandStage.VALIDATION,
            contextWithDescription
        )
        result.allowed shouldBe false
    }

    "EXECUTION → DONE should be blocked (skip VALIDATION)" {
        val v = validator()
        val result = v.canTransition(
            CommandStage.EXECUTION,
            CommandStage.DONE,
            contextWithSteps
        )
        result.allowed shouldBe false
    }

    "DONE → PLANNING should be blocked (cannot go back from DONE)" {
        val v = validator()
        val result = v.canTransition(CommandStage.DONE, CommandStage.PLANNING, emptyContext)
        result.allowed shouldBe false
    }

    "TERMINATED → any state should be blocked" {
        val v = validator()
        val blocked = listOf(
            v.canTransition(CommandStage.TERMINATED, CommandStage.PLANNING, emptyContext),
            v.canTransition(CommandStage.TERMINATED, CommandStage.EXECUTION, emptyContext),
            v.canTransition(CommandStage.TERMINATED, CommandStage.VALIDATION, emptyContext),
            v.canTransition(CommandStage.TERMINATED, CommandStage.DONE, emptyContext)
        )
        blocked.forEach { result ->
            result.allowed shouldBe false
        }
    }

    // --- getAvailableTransitions() ---

    "getAvailableTransitions from PLANNING with description should return EXECUTION" {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.PLANNING, contextWithDescription)
        val destinations = available.map { it.to }
        destinations.contains(CommandStage.EXECUTION) shouldBe true
    }

    "getAvailableTransitions from PLANNING with empty context should return EXECUTION" {
        // needsDescription not set → description is filled → EXECUTION available
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.PLANNING, emptyContext)
        val destinations = available.map { it.to }
        destinations.contains(CommandStage.EXECUTION) shouldBe true
    }

    "getAvailableTransitions from EXECUTION with steps should include VALIDATION and PLANNING" {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.EXECUTION, contextWithSteps)
        val destinations = available.map { it.to }.toSet()
        destinations.contains(CommandStage.VALIDATION) shouldBe true
        destinations.contains(CommandStage.PLANNING) shouldBe true
    }

    "getAvailableTransitions from EXECUTION without steps should only return PLANNING" {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.EXECUTION, emptyContext)
        val destinations = available.map { it.to }.toSet()
        destinations.size shouldBe 1
        destinations.contains(CommandStage.PLANNING) shouldBe true
    }

    "getAvailableTransitions from VALIDATION should return DONE and EXECUTION" {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.VALIDATION, contextWithSteps)
        val destinations = available.map { it.to }.toSet()
        destinations.contains(CommandStage.DONE) shouldBe true
        destinations.contains(CommandStage.EXECUTION) shouldBe true
    }

    "getAvailableTransitions from DONE should return TERMINATED" {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.DONE, emptyContext)
        val destinations = available.map { it.to }.toSet()
        destinations.size shouldBe 1
        destinations.contains(CommandStage.TERMINATED) shouldBe true
    }

    "getAvailableTransitions from TERMINATED should return empty list" {
        val v = validator()
        val available = v.getAvailableTransitions(CommandStage.TERMINATED, fullContext())
        available.isEmpty() shouldBe true
    }

    // --- getTransitionReason() ---

    "getTransitionReason should return null for allowed transition" {
        val v = validator()
        val reason = v.getTransitionReason(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            contextWithDescription
        )
        reason shouldBe null
    }

    "getTransitionReason should return non-null reason for blocked transition" {
        val v = validator()
        // PLANNING → DONE is always blocked (no direct transition defined)
        val reason = v.getTransitionReason(
            CommandStage.PLANNING,
            CommandStage.DONE,
            contextFull
        )
        reason shouldNotBe null
        reason!!.isNotBlank() shouldBe true
    }

    "getTransitionReason should return reason for undefined transition" {
        val v = validator()
        val reason = v.getTransitionReason(
            CommandStage.PLANNING,
            CommandStage.DONE,
            contextFull
        )
        reason shouldNotBe null
        // Blocked reason is non-empty for undefined transitions
        reason!!.isNotBlank() shouldBe true
    }

    // --- getAllTransitions() ---

    "getAllTransitions should return defensive copy" {
        val v = validator()
        val all = v.getAllTransitions()
        all.size shouldBe 6
        // Проверяем, что возвращается копия, а не оригинал
        val allAgain = v.getAllTransitions()
        all shouldNotBeSameInstanceAs allAgain
    }

    // --- buildStateMap() ---

    "buildStateMap from PLANNING should show currentState as PLANNING" {
        val v = validator()
        val map = v.buildStateMap(CommandStage.PLANNING, contextWithDescription)
        map.currentState shouldBe CommandStage.PLANNING
        map.states.size shouldBe 5
    }

    "buildStateMap should mark current state" {
        val v = validator()
        val map = v.buildStateMap(CommandStage.EXECUTION, contextWithSteps)
        val currentInfo = map.states.find { it.state == CommandStage.EXECUTION }
        currentInfo shouldNotBe null
        currentInfo!!.isCurrent shouldBe true
    }

    "buildStateMap should mark available states correctly from PLANNING" {
        val v = validator()
        val map = v.buildStateMap(CommandStage.PLANNING, contextWithDescription)

        val executionInfo = map.states.find { it.state == CommandStage.EXECUTION }
        executionInfo shouldNotBe null
        executionInfo!!.isAvailable shouldBe true

        val doneInfo = map.states.find { it.state == CommandStage.DONE }
        doneInfo shouldNotBe null
        doneInfo!!.isAvailable shouldBe false
    }

    "buildStateMap should include availableTransitions list" {
        val v = validator()
        val map = v.buildStateMap(CommandStage.VALIDATION, contextWithSteps)
        map.availableTransitions.isNotEmpty() shouldBe true
    }

    "buildStateMap should have correct reason for available state" {
        val v = validator()
        val map = v.buildStateMap(CommandStage.EXECUTION, contextWithSteps)
        val validationInfo = map.states.find { it.state == CommandStage.VALIDATION }
        validationInfo shouldNotBe null
        validationInfo!!.isAvailable shouldBe true
        // Available reason — должно быть "доступен" или непустая строка
        validationInfo.reason.isNotBlank() shouldBe true
    }

    "buildStateMap from TERMINATED should show no available transitions" {
        val v = validator()
        val map = v.buildStateMap(CommandStage.TERMINATED, fullContext())
        map.currentState shouldBe CommandStage.TERMINATED
        map.availableTransitions.isEmpty() shouldBe true
    }

    // --- Краевые случаи ---

    "transition with custom validator and no matching paths should block all" {
        // Создаём пустой граф (нет переходов)
        val emptyValidator = validator(emptyList())
        val result = emptyValidator.canTransition(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            contextWithDescription
        )
        result.allowed shouldBe false
    }

    "self-transition to same state should be blocked" {
        val v = validator()
        CommandStage.entries.forEach { stage ->
            val result = v.canTransition(stage, stage, fullContext())
            result.allowed shouldBe false
        }
    }

    "getAvailableTransitions should not include self-transitions" {
        val v = validator()
        CommandStage.entries.forEach { stage ->
            val available = v.getAvailableTransitions(stage, fullContext())
            available.forEach { transition ->
                transition.to shouldNotBe stage
            }
        }
    }

    "each plan transition should have non-blank description" {
        planTransitions().forEach { transition ->
            transition.description.isNotBlank() shouldBe true
        }
    }

    "transitions with always-true condition should work with any context" {
        val v = validator()
        // EXECUTION → PLANNING имеет condition { true }
        val contexts = listOf(emptyContext, contextWithDescription, contextWithSteps, fullContext())
        contexts.forEach { ctx ->
            val result = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, ctx)
            result.allowed shouldBe true
        }
    }
})
