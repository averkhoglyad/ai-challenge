package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Тесты для доменной модели [Task].
 */
class TaskTest : FreeSpec({

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

    "TaskId" - {

        "should throw exception when TaskId is blank" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                TaskId("")
            }
        }
    }

    "Task creation" - {

        "should create task with valid data" {
            // given
            val id = TaskId("test-id")
            val title = "Test Task"
            val now = Instant.now()

            // when
            val task = Task(
                id = id,
                title = title,
                status = TaskStatus.OPEN,
                createdAt = now,
                updatedAt = now
            )

            // then
            task.id shouldBe id
            task.title shouldBe title
            task.status shouldBe TaskStatus.OPEN
            task.createdAt shouldBe now
            task.updatedAt shouldBe now
        }

        "should throw exception when title is blank" {
            // given
            val id = TaskId("test-id")
            val now = Instant.now()

            // when & then
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

        "should create task with description" {
            // when
            val task = createTask(description = "Test description")

            // then
            task.description shouldBe "Test description"
            task.hasDescription() shouldBe true
        }

        "should create task without description" {
            // when
            val task = createTask()

            // then
            task.description shouldBe null
            task.hasDescription() shouldBe false
        }

        "should throw exception when description is blank" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                createTask(description = "   ")
            }
        }
    }

    "status checks" - {

        "should check if task is open" {
            // given
            val task = createTask(status = TaskStatus.OPEN)

            // then
            task.isOpen() shouldBe true
            task.isClosed() shouldBe false
            task.isCancelled() shouldBe false
        }

        "should check if task is closed" {
            // given
            val task = createTask(status = TaskStatus.CLOSED)

            // then
            task.isOpen() shouldBe false
            task.isClosed() shouldBe true
            task.isCancelled() shouldBe false
        }

        "should check if task is cancelled" {
            // given
            val task = createTask(status = TaskStatus.CANCELLED)

            // then
            task.isOpen() shouldBe false
            task.isClosed() shouldBe false
            task.isCancelled() shouldBe true
        }
    }

    "close / cancel" - {

        "should close task" {
            // given
            val task = createTask(status = TaskStatus.OPEN)

            // when
            val closedTask = task.close()

            // then
            closedTask.status shouldBe TaskStatus.CLOSED
            closedTask.isClosed() shouldBe true
            (closedTask.updatedAt >= task.updatedAt) shouldBe true
        }

        "should cancel task" {
            // given
            val task = createTask(status = TaskStatus.OPEN)

            // when
            val cancelledTask = task.cancel()

            // then
            cancelledTask.status shouldBe TaskStatus.CANCELLED
            cancelledTask.isCancelled() shouldBe true
            (cancelledTask.updatedAt >= task.updatedAt) shouldBe true
        }
    }

    "updateTitle" - {

        "should update task title" {
            // given
            val task = createTask(title = "Old Title")
            val newTitle = "New Title"

            // when
            val updatedTask = task.updateTitle(newTitle)

            // then
            updatedTask.title shouldBe newTitle
            (updatedTask.updatedAt >= task.updatedAt) shouldBe true
        }

        "should throw exception when updating title to blank" {
            // given
            val task = createTask(title = "Test Title")

            // when & then
            shouldThrow<IllegalArgumentException> {
                task.updateTitle("")
            }
        }
    }

    "description" - {

        "should update description" {
            // given
            val task = createTask(description = "Old description")

            // when
            val updated = task.updateDescription("New description")

            // then
            updated.description shouldBe "New description"
            (updated.updatedAt >= task.updatedAt) shouldBe true
        }

        "should throw exception when updating description to blank" {
            // given
            val task = createTask(description = "Test")

            // when & then
            shouldThrow<IllegalArgumentException> {
                task.updateDescription("")
            }
        }

        "should check hasDescription correctly" {
            // given
            val withDesc = createTask(description = "Test")
            val withoutDesc = createTask()

            // then
            withDesc.hasDescription() shouldBe true
            withoutDesc.hasDescription() shouldBe false
        }
    }
})
