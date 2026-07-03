package io.averkhogliad.ai.challenge.week3.cli.unit.cli

import io.averkhogliad.ai.challenge.week3.cli.cli.*

import io.averkhogliad.ai.challenge.week3.cli.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.cli.handlers.CommandHandler
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskStepRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
 * Тесты для [io.averkhogliad.ai.challenge.week3.cli.cli.handlers.CommandHandler] — обработчика команд CLI.
 *
 * Проверяют:
 * - Корректную обработку всех типов команд
 * - Управление состоянием через команды
 * - Взаимодействие с executor'ами
 */
class CommandHandlerTest : FreeSpec({

    /**
     * Mock TaskExecutor для тестирования
     */
    class MockTaskExecutor(
        override val taskId: TaskId,
        override val metadata: TaskMetadata = TaskMetadata(
            id = taskId,
            title = "Mock Task ${taskId.value}",
            description = "Mock task description"
        ),
        private val resultToReturn: TaskResult = TaskResult.Success("mock result")
    ) : TaskExecutor {
        var lastPrompt: Prompt? = null
        var lastConfig: TaskExecutionConfig? = null

        override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
            lastPrompt = prompt
            lastConfig = config
            return resultToReturn
        }
    }

    fun createHandler(executors: Map<TaskId, TaskExecutor>): CommandHandler =
        CommandHandler(executors)

    // ═══════════════════════════════════════════════════════════════
    // Глобальные команды
    // ═══════════════════════════════════════════════════════════════

    "Глобальные команды" - {
        "Help does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.Help, state) shouldBe state
            }
        }

        "Quit sets isRunning to false" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState(isRunning = true)
                val newState = handler.handle(Command.Quit, state)
                newState.isRunning shouldBe false
            }
        }

        "Back is no-op in CommandHandler" {
            runTest {
                val handler = createHandler(emptyMap())
                val states = listOf(
                    CliState(currentTaskId = 1),
                    CliState(currentTodoTaskId = "some-uuid"),
                    CliState(taskListMode = true)
                )

                states.forEach { state ->
                    handler.handle(Command.Back, state) shouldBe state
                }
            }
        }

        "SelectTask sets currentTaskId" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState(currentTaskId = null)
                val newState = handler.handle(Command.SelectTask(1), state)
                newState.currentTaskId shouldBe 1
            }
        }

        "SelectTask switches task" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState(currentTaskId = 1)
                val newState = handler.handle(Command.SelectTask(2), state)
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
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.SetTemperature(1.5), state)
                newState.executionConfig.temperature shouldBe 1.5
            }
        }

        "SetMaxTokens updates executionConfig" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.SetMaxTokens(2048), state)
                newState.executionConfig.maxTokens shouldBe 2048
            }
        }

        "SetStopSequences updates executionConfig" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val seq = listOf("END", "STOP")
                val newState = handler.handle(Command.SetStopSequences(seq), state)
                newState.executionConfig.stopSequences shouldBe seq
            }
        }

        "ResetParameters resets to defaults" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState(executionConfig = TaskExecutionConfig(temperature = 1.8, maxTokens = 1000))
                val newState = handler.handle(Command.ResetParameters, state)
                newState.executionConfig shouldBe TaskExecutionConfig()
            }
        }

        "ShowParameters does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ShowParameters, state)
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
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ListDialogs, state)
                newState shouldBe state
            }
        }

        "ShowHistory does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ShowHistory(), state)
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
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.SetCompressionEnabled(true), state)
                newState shouldBe state
            }
        }

        "SetCompressionWindow does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.SetCompressionWindow(10), state)
                newState shouldBe state
            }
        }

        "SetCompressionBlock does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.SetCompressionBlock(5), state)
                newState shouldBe state
            }
        }

        "ShowCompressionStatus does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ShowCompressionStatus, state)
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
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ShowStrategyMenu, state)
                newState shouldBe state
            }
        }

        "SwitchStrategy does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.SwitchStrategy(1), state)
                newState shouldBe state
            }
        }

        "ShowCurrentStrategy does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ShowCurrentStrategy, state)
                newState shouldBe state
            }
        }

        "CreateBranch does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.CreateBranch("test"), state)
                newState shouldBe state
            }
        }

        "SwitchBranch does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.SwitchBranch("test"), state)
                newState shouldBe state
            }
        }

        "ListBranches does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ListBranches, state)
                newState shouldBe state
            }
        }

        "CreateCheckpoint does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.CreateCheckpoint, state)
                newState shouldBe state
            }
        }

        "ListCheckpoints does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ListCheckpoints, state)
                newState shouldBe state
            }
        }

        "ListFacts does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ListFacts, state)
                newState shouldBe state
            }
        }

        "ClearFacts does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ClearFacts, state)
                newState shouldBe state
            }
        }

        "AddFact does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.AddFact("key", "value"), state)
                newState shouldBe state
            }
        }

        "RemoveFact does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.RemoveFact("key"), state)
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
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ClearMemory, state)
                newState shouldBe state
            }
        }

        "ShowStatus does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.ShowStatus, state)
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
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.Unknown(":foo"), state)
                newState shouldBe state
            }
        }

        "UserInput does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                val newState = handler.handle(Command.UserInput("test"), state)
                newState shouldBe state
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UserInput — интеграция с executor
    // ═══════════════════════════════════════════════════════════════

    "UserInput integration with executor" - {
        "UserInput without active task does not call executor" {
            runTest {
                val executor = MockTaskExecutor(TaskId("1"))
                val handler = createHandler(mapOf(TaskId("1") to executor))
                val state = CliState(currentTaskId = null)
                val (newState, _) = handler.executeUserInput(Command.UserInput("test"), state)
                // При одном executor'е taskId автоматически устанавливается
                newState.currentTaskId shouldBe 1
                (executor.lastPrompt != null) shouldBe true
                executor.lastPrompt!!.value shouldBe "test"
            }
        }

        "UserInput with active task calls executor" {
            runTest {
                val executor = MockTaskExecutor(TaskId("1"))
                val handler = createHandler(mapOf(TaskId("1") to executor))
                val state = CliState(currentTaskId = 1)
                val (newState, result) = handler.executeUserInput(Command.UserInput("test"), state)
                newState shouldBe state
                (result != null) shouldBe true
                result.shouldBeInstanceOf<TaskResult.Success>()
                result.content shouldBe "mock result"
            }
        }

        "executeUserInput returns result" {
            runTest {
                val executor = MockTaskExecutor(TaskId("1"))
                val handler = createHandler(mapOf(TaskId("1") to executor))
                val state = CliState(currentTaskId = 1)
                val (newState, result) = handler.executeUserInput(Command.UserInput("test"), state)
                newState shouldBe state
                (result != null) shouldBe true
                result.shouldBeInstanceOf<TaskResult.Success>()
                result.content shouldBe "mock result"
            }
        }

        "executeUserInput passes executionConfig" {
            runTest {
                val executor = MockTaskExecutor(TaskId("1"))
                val handler = createHandler(mapOf(TaskId("1") to executor))
                val config = TaskExecutionConfig(temperature = 0.5, maxTokens = 100)
                val state = CliState(currentTaskId = 1, executionConfig = config)
                handler.executeUserInput(Command.UserInput("p"), state)
                (executor.lastConfig != null) shouldBe true
                executor.lastConfig!!.temperature shouldBe 0.5
                executor.lastConfig!!.maxTokens shouldBe 100
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Executor access
    // ═══════════════════════════════════════════════════════════════

    "Executor access" - {
        "getExecutor returns executor by taskId" {
            val executor = MockTaskExecutor(TaskId("1"))
            val handler = createHandler(mapOf(TaskId("1") to executor))
            handler.getExecutor(TaskId("1")) shouldBe executor
            handler.getExecutor(TaskId("999")) shouldBe null
        }

        "getAllExecutors returns all executors" {
            val e1 = MockTaskExecutor(TaskId("1"))
            val e2 = MockTaskExecutor(TaskId("2"))
            val handler = createHandler(mapOf(TaskId("1") to e1, TaskId("2") to e2))
            val all = handler.getAllExecutors()
            all.size shouldBe 2
            all.contains(e1) shouldBe true
            all.contains(e2) shouldBe true
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Task management commands (todo-manager)
    // ═══════════════════════════════════════════════════════════════

    "Task management commands" - {
        "todo commands are no-op in CommandHandler" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()

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
            handler = CommandHandler(emptyMap())
        }

        suspend fun openTask(): CliState {
            createdTask = executor.addTask("Test Task")
            executor.openTask(createdTask.id)
            return CliState(currentTodoTaskId = createdTask.id.value)
        }

        "step commands are no-op in CommandHandler" {
            runTest {
                val state = openTask()

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
