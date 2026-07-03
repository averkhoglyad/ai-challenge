package io.averkhogliad.ai.challenge.week4.cli.unit.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Тесты для контракта интерфейса [TaskRepository].
 * Использует in-memory реализацию для проверки контракта.
 */
class TaskRepositoryTest : FreeSpec({

    lateinit var repository: TaskRepository

    fun createTask(
        id: String,
        title: String,
        status: TaskStatus = TaskStatus.OPEN
    ): Task = Task(
        id = TaskId(id),
        title = title,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    beforeEach {
        repository = InMemoryTaskRepository()
    }

    "findById" - {

        "should save and find task by id" {
            runTest {
                // given
                val task = createTask("task-1", "Test Task")
                repository.save(task)

                // when
                val found = repository.findById(TaskId("task-1"))

                // then
                found shouldNotBe null
                found!!.id shouldBe task.id
                found.title shouldBe task.title
                found.status shouldBe task.status
            }
        }

        "should return null when task not found" {
            runTest {
                // when
                val found = repository.findById(TaskId("non-existent"))

                // then
                found shouldBe null
            }
        }
    }

    "findAll" - {

        "should find all tasks" {
            runTest {
                // given
                val task1 = createTask("task-1", "Task 1")
                val task2 = createTask("task-2", "Task 2")
                val task3 = createTask("task-3", "Task 3")

                repository.save(task1)
                repository.save(task2)
                repository.save(task3)

                // when
                val all = repository.findAll()

                // then
                all.size shouldBe 3
            }
        }
    }

    "delete" - {

        "should delete task" {
            runTest {
                // given
                val task = createTask("task-1", "Test Task")
                repository.save(task)

                // when
                repository.delete(TaskId("task-1"))

                // then
                repository.exists(TaskId("task-1")) shouldBe false
                repository.findById(TaskId("task-1")) shouldBe null
            }
        }
    }

    "exists" - {

        "should check if task exists" {
            runTest {
                // given
                val task = createTask("task-1", "Test Task")
                repository.save(task)

                // then
                repository.exists(TaskId("task-1")) shouldBe true
                repository.exists(TaskId("non-existent")) shouldBe false
            }
        }
    }

    "save" - {

        "should update task on save" {
            runTest {
                // given
                val task = createTask("task-1", "Original Title")
                repository.save(task)

                val updatedTask = task.updateTitle("Updated Title")

                // when
                repository.save(updatedTask)

                // then
                val found = repository.findById(TaskId("task-1"))
                found shouldNotBe null
                found!!.title shouldBe "Updated Title"
            }
        }

        "should handle different task statuses" {
            runTest {
                // given
                val openTask = createTask("open", "Open Task", TaskStatus.OPEN)
                val closedTask = createTask("closed", "Closed Task", TaskStatus.CLOSED)
                val cancelledTask = createTask("cancelled", "Cancelled Task", TaskStatus.CANCELLED)

                repository.save(openTask)
                repository.save(closedTask)
                repository.save(cancelledTask)

                // then
                repository.findById(TaskId("open"))?.status shouldBe TaskStatus.OPEN
                repository.findById(TaskId("closed"))?.status shouldBe TaskStatus.CLOSED
                repository.findById(TaskId("cancelled"))?.status shouldBe TaskStatus.CANCELLED
            }
        }
    }
}) {
    /**
     * In-memory реализация TaskRepository для тестирования.
     */
    private class InMemoryTaskRepository : TaskRepository {
        private val tasks = mutableMapOf<String, Task>()

        override suspend fun save(task: Task) {
            tasks[task.id.value] = task
        }

        override suspend fun findById(id: TaskId): Task? {
            return tasks[id.value]
        }

        override suspend fun findAll(): List<Task> {
            return tasks.values.toList()
        }

        override suspend fun delete(id: TaskId) {
            tasks.remove(id.value)
        }

        override suspend fun exists(id: TaskId): Boolean {
            return tasks.containsKey(id.value)
        }

        override suspend fun saveSteps(
            taskId: TaskId,
            steps: List<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep>
        ) {
            // No-op for tests
        }

        override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep> {
            return emptyList()
        }

        override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
            Result.success(Unit)

        override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
            Result.success(Unit)
    }
}
