package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class TaskTest : FreeSpec({

    "Task" - {

        fun createTask(
            id: String = "test-id",
            title: String = "Test Task",
            description: String? = null,
            status: TaskStatus = TaskStatus.OPEN,
            createdAt: Instant = Instant.now(),
            updatedAt: Instant = Instant.now()
        ): Task = Task(
            id = TaskId(id),
            title = title,
            description = description,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

        "should create task with valid data" {
            val id = TaskId("test-id")
            val title = "Test Task"
            val now = Instant.now()

            val task = Task(
                id = id,
                title = title,
                status = TaskStatus.OPEN,
                createdAt = now,
                updatedAt = now
            )

            task.id shouldBe id
            task.title shouldBe title
            task.status shouldBe TaskStatus.OPEN
            task.createdAt shouldBe now
            task.updatedAt shouldBe now
        }

        "should throw exception when title is blank" {
            val id = TaskId("test-id")
            val now = Instant.now()

            shouldThrow<IllegalArgumentException> {
                Task(
                    id = id,
                    title = "",
                    status = TaskStatus.OPEN,
                    createdAt = now,
                    updatedAt = now
                )
            }
        }

        "should throw exception when TaskId is blank" {
            shouldThrow<IllegalArgumentException> {
                TaskId("")
            }
        }

        "should check if task is open" {
            val task = createTask(status = TaskStatus.OPEN)
            task.isOpen() shouldBe true
            task.isClosed() shouldBe false
            task.isCancelled() shouldBe false
        }

        "should check if task is closed" {
            val task = createTask(status = TaskStatus.CLOSED)
            task.isOpen() shouldBe false
            task.isClosed() shouldBe true
            task.isCancelled() shouldBe false
        }

        "should check if task is cancelled" {
            val task = createTask(status = TaskStatus.CANCELLED)
            task.isOpen() shouldBe false
            task.isClosed() shouldBe false
            task.isCancelled() shouldBe true
        }

        "should close task" {
            val task = createTask(status = TaskStatus.OPEN)
            val closedTask = task.close()

            closedTask.status shouldBe TaskStatus.CLOSED
            closedTask.isClosed() shouldBe true
            (closedTask.updatedAt >= task.updatedAt) shouldBe true
        }

        "should cancel task" {
            val task = createTask(status = TaskStatus.OPEN)
            val cancelledTask = task.cancel()

            cancelledTask.status shouldBe TaskStatus.CANCELLED
            cancelledTask.isCancelled() shouldBe true
            (cancelledTask.updatedAt >= task.updatedAt) shouldBe true
        }

        "should update task title" {
            val task = createTask(title = "Old Title")
            val newTitle = "New Title"
            val updatedTask = task.updateTitle(newTitle)

            updatedTask.title shouldBe newTitle
            (updatedTask.updatedAt >= task.updatedAt) shouldBe true
        }

        "should throw exception when updating title to blank" {
            val task = createTask(title = "Test Title")

            shouldThrow<IllegalArgumentException> {
                task.updateTitle("")
            }
        }

        "should create task with description" {
            val task = createTask(description = "Test description")

            task.description shouldBe "Test description"
            task.hasDescription() shouldBe true
        }

        "should create task without description" {
            val task = createTask()

            task.description shouldBe null
            task.hasDescription() shouldBe false
        }

        "should throw exception when description is blank" {
            shouldThrow<IllegalArgumentException> {
                createTask(description = "   ")
            }
        }

        "should update description" {
            val task = createTask(description = "Old description")
            val updated = task.updateDescription("New description")

            updated.description shouldBe "New description"
            (updated.updatedAt >= task.updatedAt) shouldBe true
        }

        "should throw exception when updating description to blank" {
            val task = createTask(description = "Test")

            shouldThrow<IllegalArgumentException> {
                task.updateDescription("")
            }
        }

        "should check hasDescription correctly" {
            val withDesc = createTask(description = "Test")
            withDesc.hasDescription() shouldBe true

            val withoutDesc = createTask()
            withoutDesc.hasDescription() shouldBe false
        }
    }
})
