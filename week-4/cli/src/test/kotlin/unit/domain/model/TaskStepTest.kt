package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStepId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Тесты для доменной модели [TaskStep] и value object [TaskStepId].
 */
class TaskStepTest : FreeSpec({

    fun createTestStep(
        stepId: String = "step-1",
        taskId: String = "task-1",
        text: String = "Implement login",
        isCompleted: Boolean = false,
        order: Int = 0
    ): TaskStep = TaskStep(
        id = TaskStepId(stepId),
        taskId = TaskId(taskId),
        text = text,
        isCompleted = isCompleted,
        order = order,
        createdAt = Instant.now()
    )

    "TaskStepId" - {

        "should throw when TaskStepId is blank" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                TaskStepId("")
            }
        }

        "should throw when TaskStepId is whitespace" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                TaskStepId("   ")
            }
        }
    }

    "TaskStep creation" - {

        "should create with valid data" {
            // given
            val stepId = TaskStepId("step-1")
            val taskId = TaskId("task-1")
            val now = Instant.now()

            // when
            val step = TaskStep(
                id = stepId,
                taskId = taskId,
                text = "Implement login",
                isCompleted = false,
                order = 0,
                createdAt = now
            )

            // then
            step.id shouldBe stepId
            step.taskId shouldBe taskId
            step.text shouldBe "Implement login"
            step.isCompleted shouldBe false
            step.order shouldBe 0
            step.createdAt shouldBe now
        }
    }

    "TaskStep validation" - {

        "should throw when text is blank" {
            // given
            val stepId = TaskStepId("step-1")
            val taskId = TaskId("task-1")

            // when & then
            shouldThrow<IllegalArgumentException> {
                TaskStep(
                    id = stepId,
                    taskId = taskId,
                    text = "",
                    isCompleted = false,
                    order = 0,
                    createdAt = Instant.now()
                )
            }
        }

        "should throw when text is whitespace" {
            // given
            val stepId = TaskStepId("step-1")
            val taskId = TaskId("task-1")

            // when & then
            shouldThrow<IllegalArgumentException> {
                TaskStep(
                    id = stepId,
                    taskId = taskId,
                    text = "   ",
                    isCompleted = false,
                    order = 0,
                    createdAt = Instant.now()
                )
            }
        }

        "should throw when order is negative" {
            // given
            val stepId = TaskStepId("step-1")
            val taskId = TaskId("task-1")

            // when & then
            shouldThrow<IllegalArgumentException> {
                TaskStep(
                    id = stepId,
                    taskId = taskId,
                    text = "Valid text",
                    isCompleted = false,
                    order = -1,
                    createdAt = Instant.now()
                )
            }
        }
    }

    "markCompleted / markIncomplete" - {

        "should mark completed" {
            // given
            val step = createTestStep(isCompleted = false)

            // when
            val completedStep = step.markCompleted()

            // then
            completedStep.isCompleted shouldBe true
            // Исходный шаг не изменился (иммутабельность)
            step.isCompleted shouldBe false
        }

        "should mark incomplete" {
            // given
            val step = createTestStep(isCompleted = true)

            // when
            val incompleteStep = step.markIncomplete()

            // then
            incompleteStep.isCompleted shouldBe false
            // Исходный шаг не изменился (иммутабельность)
            step.isCompleted shouldBe true
        }

        "should preserve fields after markCompleted" {
            // given
            val step = createTestStep()

            // when
            val completedStep = step.markCompleted()

            // then
            completedStep.id shouldBe step.id
            completedStep.taskId shouldBe step.taskId
            completedStep.text shouldBe step.text
            completedStep.order shouldBe step.order
            completedStep.createdAt shouldBe step.createdAt
        }
    }

    "updateText" - {

        "should update text" {
            // given
            val step = createTestStep(text = "Old text")

            // when
            val updatedStep = step.updateText("New text")

            // then
            updatedStep.text shouldBe "New text"
            // Исходный шаг не изменился (иммутабельность)
            step.text shouldBe "Old text"
        }

        "should throw when updating text with blank" {
            // given
            val step = createTestStep()

            // when & then
            shouldThrow<IllegalArgumentException> {
                step.updateText("")
            }
        }

        "should throw when updating text with whitespace" {
            // given
            val step = createTestStep()

            // when & then
            shouldThrow<IllegalArgumentException> {
                step.updateText("   ")
            }
        }

        "should preserve fields after updateText" {
            // given
            val step = createTestStep()

            // when
            val updatedStep = step.updateText("Changed text")

            // then
            updatedStep.id shouldBe step.id
            updatedStep.taskId shouldBe step.taskId
            updatedStep.isCompleted shouldBe step.isCompleted
            updatedStep.order shouldBe step.order
            updatedStep.createdAt shouldBe step.createdAt
        }
    }
})
