package io.averkhogliad.ai.challenge.week4.cli.unit.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.Transition
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TransitionValidator
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

    "planTransitions" - {

        "should return exactly 6 transitions" {
            // when
            val transitions = planTransitions()

            // then
            transitions.size shouldBe 6
        }

        "should cover all expected edges" {
            // when
            val edges = planTransitions().map { it.from to it.to }.toSet()

            // then
            edges.contains(CommandStage.PLANNING to CommandStage.EXECUTION) shouldBe true
            edges.contains(CommandStage.EXECUTION to CommandStage.VALIDATION) shouldBe true
            edges.contains(CommandStage.EXECUTION to CommandStage.PLANNING) shouldBe true
            edges.contains(CommandStage.VALIDATION to CommandStage.DONE) shouldBe true
            edges.contains(CommandStage.VALIDATION to CommandStage.EXECUTION) shouldBe true
            edges.contains(CommandStage.DONE to CommandStage.TERMINATED) shouldBe true
        }
    }

    "canTransition" - {

        "PLANNING → EXECUTION should be allowed when description filled" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.PLANNING,
                CommandStage.EXECUTION,
                contextWithDescription
            )

            // then
            result.allowed shouldBe true
        }

        "PLANNING → EXECUTION should be blocked when description not filled" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.PLANNING,
                CommandStage.EXECUTION,
                contextNeedsDescription
            )

            // then
            result.allowed shouldBe false
            result.reason.isNotBlank() shouldBe true
        }

        "PLANNING → EXECUTION should be allowed with empty context (no needsDescription flag)" {
            // When no needsDescription flag is set, description is considered filled
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.PLANNING,
                CommandStage.EXECUTION,
                emptyContext
            )

            // then
            result.allowed shouldBe true
        }

        "EXECUTION → VALIDATION should be allowed when steps are present" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.EXECUTION,
                CommandStage.VALIDATION,
                contextWithSteps
            )

            // then
            result.allowed shouldBe true
        }

        "EXECUTION → VALIDATION should be blocked when steps are empty" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.EXECUTION,
                CommandStage.VALIDATION,
                emptyContext
            )

            // then
            result.allowed shouldBe false
        }

        "EXECUTION → PLANNING should always be allowed (rollback)" {
            // given
            val v = validator()

            // when
            val result1 = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, emptyContext)
            val result2 = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, contextWithSteps)

            // then
            result1.allowed shouldBe true
            result2.allowed shouldBe true
        }

        "VALIDATION → DONE should be allowed when steps are present" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.VALIDATION,
                CommandStage.DONE,
                contextWithSteps
            )

            // then
            result.allowed shouldBe true
        }

        "VALIDATION → DONE should be blocked when steps are empty" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.VALIDATION,
                CommandStage.DONE,
                emptyContext
            )

            // then
            result.allowed shouldBe false
        }

        "VALIDATION → EXECUTION should always be allowed (edit)" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(CommandStage.VALIDATION, CommandStage.EXECUTION, emptyContext)

            // then
            result.allowed shouldBe true
        }

        "DONE → TERMINATED should always be allowed" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(CommandStage.DONE, CommandStage.TERMINATED, emptyContext)

            // then
            result.allowed shouldBe true
        }

        "PLANNING → DONE should be blocked (skip EXECUTION + VALIDATION)" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.PLANNING,
                CommandStage.DONE,
                contextFull
            )

            // then
            result.allowed shouldBe false
        }

        "PLANNING → VALIDATION should be blocked (skip EXECUTION)" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.PLANNING,
                CommandStage.VALIDATION,
                contextWithDescription
            )

            // then
            result.allowed shouldBe false
        }

        "EXECUTION → DONE should be blocked (skip VALIDATION)" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(
                CommandStage.EXECUTION,
                CommandStage.DONE,
                contextWithSteps
            )

            // then
            result.allowed shouldBe false
        }

        "DONE → PLANNING should be blocked (cannot go back from DONE)" {
            // given
            val v = validator()

            // when
            val result = v.canTransition(CommandStage.DONE, CommandStage.PLANNING, emptyContext)

            // then
            result.allowed shouldBe false
        }

        "TERMINATED → any state should be blocked" {
            // given
            val v = validator()

            // when
            val blocked = listOf(
                v.canTransition(CommandStage.TERMINATED, CommandStage.PLANNING, emptyContext),
                v.canTransition(CommandStage.TERMINATED, CommandStage.EXECUTION, emptyContext),
                v.canTransition(CommandStage.TERMINATED, CommandStage.VALIDATION, emptyContext),
                v.canTransition(CommandStage.TERMINATED, CommandStage.DONE, emptyContext)
            )

            // then
            blocked.forEach { result ->
                result.allowed shouldBe false
            }
        }
    }

    "getAvailableTransitions" - {

        "from PLANNING with description should return EXECUTION" {
            // given
            val v = validator()

            // when
            val available = v.getAvailableTransitions(CommandStage.PLANNING, contextWithDescription)

            // then
            val destinations = available.map { it.to }
            destinations.contains(CommandStage.EXECUTION) shouldBe true
        }

        "from PLANNING with empty context should return EXECUTION" {
            // needsDescription not set → description is filled → EXECUTION available
            // given
            val v = validator()

            // when
            val available = v.getAvailableTransitions(CommandStage.PLANNING, emptyContext)

            // then
            val destinations = available.map { it.to }
            destinations.contains(CommandStage.EXECUTION) shouldBe true
        }

        "from EXECUTION with steps should include VALIDATION and PLANNING" {
            // given
            val v = validator()

            // when
            val available = v.getAvailableTransitions(CommandStage.EXECUTION, contextWithSteps)

            // then
            val destinations = available.map { it.to }.toSet()
            destinations.contains(CommandStage.VALIDATION) shouldBe true
            destinations.contains(CommandStage.PLANNING) shouldBe true
        }

        "from EXECUTION without steps should only return PLANNING" {
            // given
            val v = validator()

            // when
            val available = v.getAvailableTransitions(CommandStage.EXECUTION, emptyContext)

            // then
            val destinations = available.map { it.to }.toSet()
            destinations.size shouldBe 1
            destinations.contains(CommandStage.PLANNING) shouldBe true
        }

        "from VALIDATION should return DONE and EXECUTION" {
            // given
            val v = validator()

            // when
            val available = v.getAvailableTransitions(CommandStage.VALIDATION, contextWithSteps)

            // then
            val destinations = available.map { it.to }.toSet()
            destinations.contains(CommandStage.DONE) shouldBe true
            destinations.contains(CommandStage.EXECUTION) shouldBe true
        }

        "from DONE should return TERMINATED" {
            // given
            val v = validator()

            // when
            val available = v.getAvailableTransitions(CommandStage.DONE, emptyContext)

            // then
            val destinations = available.map { it.to }.toSet()
            destinations.size shouldBe 1
            destinations.contains(CommandStage.TERMINATED) shouldBe true
        }

        "from TERMINATED should return empty list" {
            // given
            val v = validator()

            // when
            val available = v.getAvailableTransitions(CommandStage.TERMINATED, fullContext())

            // then
            available.isEmpty() shouldBe true
        }
    }

    "getTransitionReason" - {

        "should return null for allowed transition" {
            // given
            val v = validator()

            // when
            val reason = v.getTransitionReason(
                CommandStage.PLANNING,
                CommandStage.EXECUTION,
                contextWithDescription
            )

            // then
            reason shouldBe null
        }

        "should return non-null reason for blocked transition" {
            // given
            val v = validator()

            // when
            // PLANNING → DONE is always blocked (no direct transition defined)
            val reason = v.getTransitionReason(
                CommandStage.PLANNING,
                CommandStage.DONE,
                contextFull
            )

            // then
            reason shouldNotBe null
            reason!!.isNotBlank() shouldBe true
        }

        "should return reason for undefined transition" {
            // given
            val v = validator()

            // when
            val reason = v.getTransitionReason(
                CommandStage.PLANNING,
                CommandStage.DONE,
                contextFull
            )

            // then
            reason shouldNotBe null
            // Blocked reason is non-empty for undefined transitions
            reason!!.isNotBlank() shouldBe true
        }
    }

    "getAllTransitions" - {

        "should return defensive copy" {
            // given
            val v = validator()

            // when
            val all = v.getAllTransitions()
            val allAgain = v.getAllTransitions()

            // then
            all.size shouldBe 6
            // Проверяем, что возвращается копия, а не оригинал
            all shouldNotBeSameInstanceAs allAgain
        }
    }

    "buildStateMap" - {

        "from PLANNING should show currentState as PLANNING" {
            // given
            val v = validator()

            // when
            val map = v.buildStateMap(CommandStage.PLANNING, contextWithDescription)

            // then
            map.currentState shouldBe CommandStage.PLANNING
            map.states.size shouldBe 5
        }

        "should mark current state" {
            // given
            val v = validator()

            // when
            val map = v.buildStateMap(CommandStage.EXECUTION, contextWithSteps)

            // then
            val currentInfo = map.states.find { it.state == CommandStage.EXECUTION }
            currentInfo shouldNotBe null
            currentInfo!!.isCurrent shouldBe true
        }

        "should mark available states correctly from PLANNING" {
            // given
            val v = validator()

            // when
            val map = v.buildStateMap(CommandStage.PLANNING, contextWithDescription)

            // then
            val executionInfo = map.states.find { it.state == CommandStage.EXECUTION }
            executionInfo shouldNotBe null
            executionInfo!!.isAvailable shouldBe true

            val doneInfo = map.states.find { it.state == CommandStage.DONE }
            doneInfo shouldNotBe null
            doneInfo!!.isAvailable shouldBe false
        }

        "should include availableTransitions list" {
            // given
            val v = validator()

            // when
            val map = v.buildStateMap(CommandStage.VALIDATION, contextWithSteps)

            // then
            map.availableTransitions.isNotEmpty() shouldBe true
        }

        "should have correct reason for available state" {
            // given
            val v = validator()

            // when
            val map = v.buildStateMap(CommandStage.EXECUTION, contextWithSteps)

            // then
            val validationInfo = map.states.find { it.state == CommandStage.VALIDATION }
            validationInfo shouldNotBe null
            validationInfo!!.isAvailable shouldBe true
            // Available reason — должно быть "доступен" или непустая строка
            validationInfo.reason.isNotBlank() shouldBe true
        }

        "from TERMINATED should show no available transitions" {
            // given
            val v = validator()

            // when
            val map = v.buildStateMap(CommandStage.TERMINATED, fullContext())

            // then
            map.currentState shouldBe CommandStage.TERMINATED
            map.availableTransitions.isEmpty() shouldBe true
        }
    }

    "edge cases" - {

        "transition with custom validator and no matching paths should block all" {
            // given
            // Создаём пустой граф (нет переходов)
            val emptyValidator = validator(emptyList())

            // when
            val result = emptyValidator.canTransition(
                CommandStage.PLANNING,
                CommandStage.EXECUTION,
                contextWithDescription
            )

            // then
            result.allowed shouldBe false
        }

        "self-transition to same state should be blocked" {
            // given
            val v = validator()

            // when / then
            CommandStage.entries.forEach { stage ->
                val result = v.canTransition(stage, stage, fullContext())
                result.allowed shouldBe false
            }
        }

        "getAvailableTransitions should not include self-transitions" {
            // given
            val v = validator()

            // when / then
            CommandStage.entries.forEach { stage ->
                val available = v.getAvailableTransitions(stage, fullContext())
                available.forEach { transition ->
                    transition.to shouldNotBe stage
                }
            }
        }

        "each plan transition should have non-blank description" {
            // when / then
            planTransitions().forEach { transition ->
                transition.description.isNotBlank() shouldBe true
            }
        }

        "transitions with always-true condition should work with any context" {
            // given
            val v = validator()
            // EXECUTION → PLANNING имеет condition { true }
            val contexts = listOf(emptyContext, contextWithDescription, contextWithSteps, fullContext())

            // when / then
            contexts.forEach { ctx ->
                val result = v.canTransition(CommandStage.EXECUTION, CommandStage.PLANNING, ctx)
                result.allowed shouldBe true
            }
        }
    }
})
