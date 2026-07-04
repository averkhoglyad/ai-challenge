package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta
import io.averkhogliad.ai.challenge.week4.cli.domain.model.applyDelta
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Тесты для [TaskState] и функции [applyDelta].
 */
class TaskStateTest : FreeSpec({

    "applyDelta — SetGoal" - {

        "should set goal" {
            // given
            val state = TaskState.EMPTY
            val delta = TaskStateDelta.SetGoal("Build a REST API")

            // when
            val result = state.applyDelta(delta)

            // then
            result.goal shouldBe "Build a REST API"
            result.lastUpdated shouldNotBe null
        }

        "should replace existing goal" {
            // given
            val state = TaskState(goal = "Old goal")
            val delta = TaskStateDelta.SetGoal("New goal")

            // when
            val result = state.applyDelta(delta)

            // then
            result.goal shouldBe "New goal"
        }

        "should set goal to null when blank text" {
            // given
            val state = TaskState(goal = "Some goal")
            val delta = TaskStateDelta.SetGoal("  ")

            // when
            val result = state.applyDelta(delta)

            // then
            result.goal shouldBe null
        }
    }

    "applyDelta — AddTerm" - {

        "should add new term" {
            // given
            val state = TaskState.EMPTY
            val delta = TaskStateDelta.AddTerm("API", "Application Programming Interface")

            // when
            val result = state.applyDelta(delta)

            // then
            result.definedTerms.size shouldBe 1
            result.definedTerms[0] shouldBe ("API" to "Application Programming Interface")
        }

        "should add term with same name (duplicate)" {
            // given
            val state = TaskState(definedTerms = listOf("API" to "Old definition"))
            val delta = TaskStateDelta.AddTerm("API", "New definition")

            // when
            val result = state.applyDelta(delta)

            // then
            result.definedTerms.size shouldBe 2
            result.definedTerms[0] shouldBe ("API" to "Old definition")
            result.definedTerms[1] shouldBe ("API" to "New definition")
        }
    }

    "applyDelta — RemoveTerm" - {

        "should remove existing term" {
            // given
            val state = TaskState(definedTerms = listOf("API" to "def1", "REST" to "def2"))
            val delta = TaskStateDelta.RemoveTerm("API")

            // when
            val result = state.applyDelta(delta)

            // then
            result.definedTerms.size shouldBe 1
            result.definedTerms[0] shouldBe ("REST" to "def2")
        }

        "should leave state unchanged when term not found" {
            // given
            val state = TaskState(definedTerms = listOf("API" to "def"))
            val delta = TaskStateDelta.RemoveTerm("NonExistent")

            // when
            val result = state.applyDelta(delta)

            // then
            result.definedTerms.size shouldBe 1
            result.definedTerms[0] shouldBe ("API" to "def")
        }
    }

    "applyDelta — AddConstraint" - {

        "should add constraint" {
            // given
            val state = TaskState.EMPTY
            val delta = TaskStateDelta.AddConstraint("Must use Kotlin")

            // when
            val result = state.applyDelta(delta)

            // then
            result.constraints.size shouldBe 1
            result.constraints[0] shouldBe "Must use Kotlin"
        }

        "should add multiple constraints" {
            // given
            val state = TaskState(constraints = listOf("Constraint 1"))
            val delta1 = TaskStateDelta.AddConstraint("Constraint 2")
            val delta2 = TaskStateDelta.AddConstraint("Constraint 3")

            // when
            val result = state.applyDelta(delta1).applyDelta(delta2)

            // then
            result.constraints.size shouldBe 3
            result.constraints[0] shouldBe "Constraint 1"
            result.constraints[1] shouldBe "Constraint 2"
            result.constraints[2] shouldBe "Constraint 3"
        }
    }

    "applyDelta — RemoveConstraint" - {

        "should remove constraint by index" {
            // given
            val state = TaskState(constraints = listOf("C1", "C2", "C3"))
            val delta = TaskStateDelta.RemoveConstraint(1)

            // when
            val result = state.applyDelta(delta)

            // then
            result.constraints.size shouldBe 2
            result.constraints[0] shouldBe "C1"
            result.constraints[1] shouldBe "C3"
        }

        "should remove first constraint by index 0" {
            // given
            val state = TaskState(constraints = listOf("C1", "C2"))
            val delta = TaskStateDelta.RemoveConstraint(0)

            // when
            val result = state.applyDelta(delta)

            // then
            result.constraints.size shouldBe 1
            result.constraints[0] shouldBe "C2"
        }

        "should leave state unchanged for out-of-bounds index" {
            // given
            val state = TaskState(constraints = listOf("C1", "C2"))
            val delta = TaskStateDelta.RemoveConstraint(5)

            // when
            val result = state.applyDelta(delta)

            // then
            result.constraints.size shouldBe 2
        }

        "should leave state unchanged for negative index" {
            // given
            val state = TaskState(constraints = listOf("C1"))
            val delta = TaskStateDelta.RemoveConstraint(-1)

            // when
            val result = state.applyDelta(delta)

            // then
            result.constraints.size shouldBe 1
        }
    }

    "applyDelta — ResetAll" - {

        "should reset all state to empty" {
            // given
            val state = TaskState(
                goal = "Some goal",
                definedTerms = listOf("T1" to "D1"),
                constraints = listOf("C1", "C2"),
                clarifiedFacts = listOf("Fact 1")
            )
            val delta = TaskStateDelta.ResetAll

            // when
            val result = state.applyDelta(delta)

            // then
            result.goal shouldBe null
            result.definedTerms.isEmpty() shouldBe true
            result.constraints.isEmpty() shouldBe true
            result.clarifiedFacts.isEmpty() shouldBe true
        }
    }

    "applyDelta — NoChanges" - {

        "should return same state" {
            // given
            val state = TaskState(goal = "Keep me")
            val delta = TaskStateDelta.NoChanges

            // when
            val result = state.applyDelta(delta)

            // then
            result shouldBe state
            result.goal shouldBe "Keep me"
        }
    }

    "applyDelta — Composite" - {

        "should apply all deltas in order" {
            // given
            val state = TaskState.EMPTY
            val delta = TaskStateDelta.Composite(
                listOf(
                    TaskStateDelta.SetGoal("My Goal"),
                    TaskStateDelta.AddTerm("T1", "D1"),
                    TaskStateDelta.AddConstraint("C1"),
                    TaskStateDelta.RemoveTerm("T1")
                )
            )

            // when
            val result = state.applyDelta(delta)

            // then
            result.goal shouldBe "My Goal"
            result.constraints.size shouldBe 1
            result.constraints[0] shouldBe "C1"
            // Term was added then removed
            result.definedTerms.isEmpty() shouldBe true
        }

        "should handle empty Composite as NoChanges" {
            // given
            val state = TaskState(goal = "Keep")
            val delta = TaskStateDelta.Composite(emptyList())

            // when
            val result = state.applyDelta(delta)

            // then
            result.goal shouldBe "Keep"
        }

        "should handle nested Composite" {
            // given
            val state = TaskState.EMPTY
            val inner = TaskStateDelta.Composite(
                listOf(
                    TaskStateDelta.AddTerm("Inner1", "D1")
                )
            )
            val outer = TaskStateDelta.Composite(
                listOf(
                    TaskStateDelta.SetGoal("Outer Goal"),
                    inner
                )
            )

            // when
            val result = state.applyDelta(outer)

            // then
            result.goal shouldBe "Outer Goal"
            result.definedTerms.size shouldBe 1
            result.definedTerms[0] shouldBe ("Inner1" to "D1")
        }
    }

    "applyDelta — idempotency" - {

        "should be idempotent for SetGoal" {
            // given
            val state = TaskState.EMPTY
            val delta = TaskStateDelta.SetGoal("Goal")

            // when
            val first = state.applyDelta(delta)
            val second = first.applyDelta(delta)

            // then
            second.goal shouldBe "Goal"
        }

        "should be idempotent for NoChanges" {
            // given
            val state = TaskState(goal = "Goal")
            val delta = TaskStateDelta.NoChanges

            // when
            val first = state.applyDelta(delta)
            val second = first.applyDelta(delta)

            // then
            second.goal shouldBe "Goal"
        }
    }

    "TaskState.EMPTY" - {

        "should have all fields empty" {
            TaskState.EMPTY.goal shouldBe null
            TaskState.EMPTY.definedTerms.isEmpty() shouldBe true
            TaskState.EMPTY.constraints.isEmpty() shouldBe true
            TaskState.EMPTY.clarifiedFacts.isEmpty() shouldBe true
        }
    }
})
