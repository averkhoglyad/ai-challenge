package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.CommandHandler
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskStepRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.*

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

    override suspend fun saveSteps(taskId: TaskId, steps: List<TaskStep>) {
        // No-op for tests
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep> {
        return emptyList()
    }

    override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
        Result.success(Unit)
}

/**
 * Тесты для [io.averkhogliad.ai.challenge.week4.cli.cli.handlers.CommandHandler] — обработчика команд CLI.
 *
 * Проверяют:
 * - Корректную обработку всех типов команд
 * - Управление состоянием через команды
 * - Взаимодействие с executor'ами
 */
class CommandHandlerTest : FreeSpec({

    // ═══════════════════════════════════════════════════════════════
    // Глобальные команды
    // ═══════════════════════════════════════════════════════════════

    "Глобальные команды" - {
        "Help does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val result = handler.handle(Command.Help, state)

                // then
                result shouldBe state
            }
        }

        "Quit sets isRunning to false" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState(isRunning = true)

                // when
                val newState = handler.handle(Command.Quit, state)

                // then
                newState.isRunning shouldBe false
            }
        }

        "Back is no-op in CommandHandler" {
            runTest {
                // given
                val handler = CommandHandler()
                val states = listOf(
                    CliState(currentTaskId = 1),
                    CliState(currentTodoTaskId = "some-uuid"),
                    CliState(taskListMode = true)
                )

                // when & then
                states.forEach { state ->
                    handler.handle(Command.Back, state) shouldBe state
                }
            }
        }

        "SelectTask sets currentTaskId" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState(currentTaskId = null)

                // when
                val newState = handler.handle(Command.SelectTask(1), state)

                // then
                newState.currentTaskId shouldBe 1
            }
        }

        "SelectTask switches task" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState(currentTaskId = 1)

                // when
                val newState = handler.handle(Command.SelectTask(2), state)

                // then
                newState.currentTaskId shouldBe 2
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LLM параметры
    // ═══════════════════════════════════════════════════════════════

    "LLM параметры" - {
        "SetTemperature updates executionConfig" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.SetTemperature(1.5), state)

                // then
                newState.executionConfig.temperature shouldBe 1.5
            }
        }

        "SetMaxTokens updates executionConfig" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.SetMaxTokens(2048), state)

                // then
                newState.executionConfig.maxTokens shouldBe 2048
            }
        }

        "SetStopSequences updates executionConfig" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()
                val seq = listOf("END", "STOP")

                // when
                val newState = handler.handle(Command.SetStopSequences(seq), state)

                // then
                newState.executionConfig.stopSequences shouldBe seq
            }
        }

        "ResetParameters resets to defaults" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState(executionConfig = TaskExecutionConfig(temperature = 1.8, maxTokens = 1000))

                // when
                val newState = handler.handle(Command.ResetParameters, state)

                // then
                newState.executionConfig shouldBe TaskExecutionConfig()
            }
        }

        "ShowParameters does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ShowParameters, state)

                // then
                newState shouldBe state
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Dialog commands
    // ═══════════════════════════════════════════════════════════════

    "Dialog commands" - {
        "ListDialogs does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ListDialogs, state)

                // then
                newState shouldBe state
            }
        }

        "ShowHistory does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ShowHistory(), state)

                // then
                newState shouldBe state
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Compression commands (no-op)
    // ═══════════════════════════════════════════════════════════════

    "Compression commands" - {
        "SetCompressionEnabled does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.SetCompressionEnabled(true), state)

                // then
                newState shouldBe state
            }
        }

        "SetCompressionWindow does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.SetCompressionWindow(10), state)

                // then
                newState shouldBe state
            }
        }

        "SetCompressionBlock does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.SetCompressionBlock(5), state)

                // then
                newState shouldBe state
            }
        }

        "ShowCompressionStatus does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ShowCompressionStatus, state)

                // then
                newState shouldBe state
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy commands (no-op)
    // ═══════════════════════════════════════════════════════════════

    "Strategy commands" - {
        "ShowStrategyMenu does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ShowStrategyMenu, state)

                // then
                newState shouldBe state
            }
        }

        "SwitchStrategy does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.SwitchStrategy(1), state)

                // then
                newState shouldBe state
            }
        }

        "ShowCurrentStrategy does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ShowCurrentStrategy, state)

                // then
                newState shouldBe state
            }
        }

        "CreateBranch does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.CreateBranch("test"), state)

                // then
                newState shouldBe state
            }
        }

        "SwitchBranch does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.SwitchBranch("test"), state)

                // then
                newState shouldBe state
            }
        }

        "ListBranches does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ListBranches, state)

                // then
                newState shouldBe state
            }
        }

        "CreateCheckpoint does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.CreateCheckpoint, state)

                // then
                newState shouldBe state
            }
        }

        "ListCheckpoints does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ListCheckpoints, state)

                // then
                newState shouldBe state
            }
        }

        "ListFacts does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ListFacts, state)

                // then
                newState shouldBe state
            }
        }

        "ClearFacts does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ClearFacts, state)

                // then
                newState shouldBe state
            }
        }

        "AddFact does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.AddFact("key", "value"), state)

                // then
                newState shouldBe state
            }
        }

        "RemoveFact does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.RemoveFact("key"), state)

                // then
                newState shouldBe state
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Memory commands (Phase 3)
    // ═══════════════════════════════════════════════════════════════

    "Memory commands" - {
        "ClearMemory does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ClearMemory, state)

                // then
                newState shouldBe state
            }
        }

        "ShowStatus does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.ShowStatus, state)

                // then
                newState shouldBe state
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Unknown and UserInput
    // ═══════════════════════════════════════════════════════════════

    "Unknown and UserInput" - {
        "Unknown does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.Unknown(":foo"), state)

                // then
                newState shouldBe state
            }
        }

        "UserInput does not change state" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when
                val newState = handler.handle(Command.UserInput("test"), state)

                // then
                newState shouldBe state
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Task management commands (todo-manager)
    // ═══════════════════════════════════════════════════════════════

    "Task management commands" - {
        "todo commands are no-op in CommandHandler" {
            runTest {
                // given
                val handler = CommandHandler()
                val state = CliState()

                // when & then
                handler.handle(Command.AddTask("Test Task"), state) shouldBe state
                handler.handle(Command.ListTasks, state) shouldBe state
                handler.handle(Command.EditTask(null, "Updated Title"), state) shouldBe state
                handler.handle(Command.DropTask(null), state) shouldBe state
                handler.handle(Command.OpenTask(TaskId("task-1")), state) shouldBe state
                handler.handle(Command.CloseTask(null), state) shouldBe state
                handler.handle(Command.CancelTask(null), state) shouldBe state
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Step management commands (Phase 4)
    // ═══════════════════════════════════════════════════════════════

    "Step management commands" - {
        lateinit var repository: InMemoryTaskRepository
        lateinit var executor: TodoTaskService
        lateinit var stepRepository: InMemoryTaskStepRepository
        lateinit var handler: CommandHandler
        lateinit var createdTask: Task

        beforeEach {
            repository = InMemoryTaskRepository()
            executor = TodoTaskService(repository)
            stepRepository = InMemoryTaskStepRepository()
            handler = CommandHandler()
        }

        suspend fun openTask(): CliState {
            createdTask = executor.addTask("Test Task")
            executor.openTask(createdTask.id)
            return CliState(currentTodoTaskId = createdTask.id.value)
        }

        "step commands are no-op in CommandHandler" {
            runTest {
                // given
                val state = openTask()

                // when & then
                handler.handle(Command.AddStep("Do something"), state) shouldBe state
                handler.handle(Command.ListSteps, state) shouldBe state
                handler.handle(Command.CompleteStep("step-1"), state) shouldBe state
                stepRepository.findByTaskId(createdTask.id).isEmpty() shouldBe true
            }
        }
    }
})

/**
 * In-memory реализация [TaskStepRepository] для тестирования.
 */
private class InMemoryTaskStepRepository : TaskStepRepository {
    private val steps = mutableMapOf<TaskStepId, TaskStep>()

    override fun save(step: TaskStep): TaskStep {
        steps[step.id] = step
        return step
    }

    override fun findByTaskId(taskId: TaskId): List<TaskStep> {
        return steps.values
            .filter { it.taskId == taskId }
            .sortedBy { it.order }
    }

    override fun findById(stepId: TaskStepId): TaskStep? {
        return steps[stepId]
    }

    override fun delete(stepId: TaskStepId): Boolean {
        return steps.remove(stepId) != null
    }

    override fun deleteByTaskId(taskId: TaskId): Int {
        val toRemove = steps.values.filter { it.taskId == taskId }
        toRemove.forEach { steps.remove(it.id) }
        return toRemove.size
    }

    override fun countByTaskId(taskId: TaskId): Int {
        return steps.values.count { it.taskId == taskId }
    }
}
