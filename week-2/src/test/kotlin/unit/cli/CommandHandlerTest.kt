package io.averkhogliad.ai.challenge.week2.unit.cli

import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week2.cli.CliState
import io.averkhogliad.ai.challenge.week2.cli.CommandHandler
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.Task
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week2.domain.service.TaskStepRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.runTest

class InMemoryTaskRepository : TaskRepository {
    private val tasks = mutableMapOf<String, Task>()

    override suspend fun save(task: Task) {
        tasks[task.id.value] = task
    }

    override suspend fun findById(id: TaskId): Task? = tasks[id.value]
    override suspend fun findAll(): List<Task> = tasks.values.toList()
    override suspend fun delete(id: TaskId) {
        tasks.remove(id.value)
    }

    override suspend fun exists(id: TaskId): Boolean = tasks.containsKey(id.value)
    override suspend fun saveSteps(taskId: TaskId, steps: List<TaskStep>) { /* no-op */
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep> = emptyList()
}

class InMemoryTaskStepRepository : TaskStepRepository {
    private val steps = mutableMapOf<TaskStepId, TaskStep>()

    override fun save(step: TaskStep): TaskStep {
        steps[step.id] = step; return step
    }

    override fun findByTaskId(taskId: TaskId): List<TaskStep> =
        steps.values.filter { it.taskId == taskId }.sortedBy { it.order }

    override fun findById(stepId: TaskStepId): TaskStep? = steps[stepId]
    override fun delete(stepId: TaskStepId): Boolean = steps.remove(stepId) != null
    override fun deleteByTaskId(taskId: TaskId): Int {
        val toRemove = steps.values.filter { it.taskId == taskId }
        toRemove.forEach { steps.remove(it.id) }
        return toRemove.size
    }

    override fun countByTaskId(taskId: TaskId): Int = steps.values.count { it.taskId == taskId }
}

class CommandHandlerTest : FreeSpec({

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

    fun createHandler(executors: Map<TaskId, TaskExecutor>): CommandHandler = CommandHandler(executors)

    // ========================================================================
    // Глобальные команды
    // ========================================================================

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
                newState.isRunning.shouldBeFalse()
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

    // ========================================================================
    // LLM параметры
    // ========================================================================

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

    // ========================================================================
    // Dialog commands
    // ========================================================================

    "Dialog commands" - {

        "ListDialogs does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ListDialogs, state) shouldBe state
            }
        }

        "ShowHistory does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ShowHistory(), state) shouldBe state
            }
        }
    }

    // ========================================================================
    // Compression commands
    // ========================================================================

    "Compression commands" - {

        "SetCompressionEnabled does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.SetCompressionEnabled(true), state) shouldBe state
            }
        }

        "SetCompressionWindow does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.SetCompressionWindow(10), state) shouldBe state
            }
        }

        "SetCompressionBlock does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.SetCompressionBlock(5), state) shouldBe state
            }
        }

        "ShowCompressionStatus does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ShowCompressionStatus, state) shouldBe state
            }
        }
    }

    // ========================================================================
    // Strategy commands
    // ========================================================================

    "Strategy commands" - {

        "ShowStrategyMenu does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ShowStrategyMenu, state) shouldBe state
            }
        }

        "SwitchStrategy does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.SwitchStrategy(1), state) shouldBe state
            }
        }

        "ShowCurrentStrategy does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ShowCurrentStrategy, state) shouldBe state
            }
        }

        "CreateBranch does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.CreateBranch("test"), state) shouldBe state
            }
        }

        "SwitchBranch does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.SwitchBranch("test"), state) shouldBe state
            }
        }

        "ListBranches does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ListBranches, state) shouldBe state
            }
        }

        "CreateCheckpoint does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.CreateCheckpoint, state) shouldBe state
            }
        }

        "ListCheckpoints does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ListCheckpoints, state) shouldBe state
            }
        }

        "ListFacts does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ListFacts, state) shouldBe state
            }
        }

        "ClearFacts does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ClearFacts, state) shouldBe state
            }
        }

        "AddFact does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.AddFact("key", "value"), state) shouldBe state
            }
        }

        "RemoveFact does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.RemoveFact("key"), state) shouldBe state
            }
        }
    }

    // ========================================================================
    // Memory commands
    // ========================================================================

    "Memory commands" - {

        "ClearMemory does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ClearMemory, state) shouldBe state
            }
        }

        "ShowStatus does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.ShowStatus, state) shouldBe state
            }
        }
    }

    // ========================================================================
    // Unknown and UserInput
    // ========================================================================

    "Unknown and UserInput" - {

        "Unknown does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.Unknown(":foo"), state) shouldBe state
            }
        }

        "UserInput does not change state" {
            runTest {
                val handler = createHandler(emptyMap())
                val state = CliState()
                handler.handle(Command.UserInput("test"), state) shouldBe state
            }
        }
    }

    // ========================================================================
    // UserInput integration with executor
    // ========================================================================

    "UserInput integration with executor" - {

        "UserInput without active task sets auto taskId and calls executor" {
            runTest {
                val executor = MockTaskExecutor(TaskId("1"))
                val handler = createHandler(mapOf(TaskId("1") to executor))
                val state = CliState(currentTaskId = null)
                val (newState, _) = handler.executeUserInput(Command.UserInput("test"), state)
                newState.currentTaskId shouldBe 1
                executor.lastPrompt.shouldNotBeNull()
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
                result.shouldNotBeNull()
                (result as TaskResult.Success).content shouldBe "mock result"
            }
        }

        "executeUserInput returns result" {
            runTest {
                val executor = MockTaskExecutor(TaskId("1"))
                val handler = createHandler(mapOf(TaskId("1") to executor))
                val state = CliState(currentTaskId = 1)
                val (newState2, result2) = handler.executeUserInput(Command.UserInput("test"), state)
                newState2 shouldBe state
                result2.shouldNotBeNull()
                (result2 as TaskResult.Success).content shouldBe "mock result"
            }
        }

        "executeUserInput passes executionConfig" {
            runTest {
                val executor = MockTaskExecutor(TaskId("1"))
                val handler = createHandler(mapOf(TaskId("1") to executor))
                val config = TaskExecutionConfig(temperature = 0.5, maxTokens = 100)
                val state = CliState(currentTaskId = 1, executionConfig = config)
                handler.executeUserInput(Command.UserInput("p"), state)
                executor.lastConfig.shouldNotBeNull()
                executor.lastConfig!!.temperature shouldBe 0.5
                executor.lastConfig!!.maxTokens shouldBe 100
            }
        }
    }

    // ========================================================================
    // Executor access
    // ========================================================================

    "Executor access" - {

        "getExecutor returns executor by taskId" {
            val executor = MockTaskExecutor(TaskId("1"))
            val handler = createHandler(mapOf(TaskId("1") to executor))
            handler.getExecutor(TaskId("1")) shouldBeSameInstanceAs executor
            handler.getExecutor(TaskId("999")).shouldBeNull()
        }

        "getAllExecutors returns all executors" {
            val e1 = MockTaskExecutor(TaskId("1"))
            val e2 = MockTaskExecutor(TaskId("2"))
            val handler = createHandler(mapOf(TaskId("1") to e1, TaskId("2") to e2))
            val all = handler.getAllExecutors()
            all.shouldHaveSize(2)
            all.shouldContain(e1)
            all.shouldContain(e2)
        }
    }

    // ========================================================================
    // Task management commands
    // ========================================================================

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

    // ========================================================================
    // Step management commands
    // ========================================================================

    "Step management commands" - {

        "step commands are no-op in CommandHandler" {
            runTest {
                val repository = InMemoryTaskRepository()
                val executor = TodoTaskService(repository)
                val stepRepository = InMemoryTaskStepRepository()
                val handler = createHandler(emptyMap())

                val createdTask = executor.addTask("Test Task")
                executor.openTask(createdTask.id)
                val state = CliState(currentTodoTaskId = createdTask.id.value)

                handler.handle(Command.AddStep("Do something"), state) shouldBe state
                handler.handle(Command.ListSteps, state) shouldBe state
                handler.handle(Command.CompleteStep("step-1"), state) shouldBe state
                stepRepository.findByTaskId(createdTask.id).shouldBeEmpty()
            }
        }
    }
})
