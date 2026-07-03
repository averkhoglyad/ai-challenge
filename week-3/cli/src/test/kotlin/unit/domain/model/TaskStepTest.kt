package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStepId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class TaskStepTest : FreeSpec({

    "TaskStep" - {

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

        "should create TaskStep with valid data" {
            val stepId = TaskStepId("step-1")
            val taskId = TaskId("task-1")
            val now = Instant.now()

            val step = TaskStep(
                id = stepId,
                taskId = taskId,
                text = "Implement login",
                isCompleted = false,
                order = 0,
                createdAt = now
            )

            step.id shouldBe stepId
            step.taskId shouldBe taskId
            step.text shouldBe "Implement login"
            step.isCompleted shouldBe false
            step.order shouldBe 0
            step.createdAt shouldBe now
        }

        "should throw when TaskStepId value is blank" {
            shouldThrow<IllegalArgumentException> {
                TaskStepId("")
            }
        }

        "should throw when TaskStepId value is whitespace only" {
            shouldThrow<IllegalArgumentException> {
                TaskStepId("   ")
            }
        }

        "should throw when text is blank" {
            val stepId = TaskStepId("step-1")
            val taskId = TaskId("task-1")

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

        "should throw when text is whitespace only" {
            val stepId = TaskStepId("step-1")
            val taskId = TaskId("task-1")

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
            val stepId = TaskStepId("step-1")
            val taskId = TaskId("task-1")

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

        "should mark as completed" {
            val step = createTestStep(isCompleted = false)

            val completedStep = step.markCompleted()

            completedStep.isCompleted shouldBe true
            step.isCompleted shouldBe false
        }

        "should mark as incomplete" {
            val step = createTestStep(isCompleted = true)

            val incompleteStep = step.markIncomplete()

            incompleteStep.isCompleted shouldBe false
            step.isCompleted shouldBe true
        }

        "should update text with valid new text" {
            val step = createTestStep(text = "Old text")

            val updatedStep = step.updateText("New text")

            updatedStep.text shouldBe "New text"
            step.text shouldBe "Old text"
        }

        "should throw when updating text with blank value" {
            val step = createTestStep()

            shouldThrow<IllegalArgumentException> {
                step.updateText("")
            }
        }

        "should throw when updating text with whitespace only" {
            val step = createTestStep()

            shouldThrow<IllegalArgumentException> {
                step.updateText("   ")
            }
        }

        "should preserve all other fields after markCompleted" {
            val step = createTestStep()

            val completedStep = step.markCompleted()

            completedStep.id shouldBe step.id
            completedStep.taskId shouldBe step.taskId
            completedStep.text shouldBe step.text
            completedStep.order shouldBe step.order
            completedStep.createdAt shouldBe step.createdAt
        }

        "should preserve all other fields after updateText" {
            val step = createTestStep()

            val updatedStep = step.updateText("Changed text")

            updatedStep.id shouldBe step.id
            updatedStep.taskId shouldBe step.taskId
            updatedStep.isCompleted shouldBe step.isCompleted
            updatedStep.order shouldBe step.order
            updatedStep.createdAt shouldBe step.createdAt
        }
    }
})
