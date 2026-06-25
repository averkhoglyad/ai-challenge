package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.*

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
}

/**
 * Тесты для [CommandHandler] — обработчика команд CLI.
 *
 * Проверяют:
 * - Корректную обработку всех типов команд
 * - Управление состоянием через команды
 * - Взаимодействие с executor'ами
 */
@DisplayName("CommandHandler")
class CommandHandlerTest {

    /**
     * Mock TaskExecutor для тестирования
     */
    private class MockTaskExecutor(
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

    private fun createHandler(executors: Map<TaskId, TaskExecutor>): CommandHandler =
        CommandHandler(executors)

    // ═══════════════════════════════════════════════════════════════
    // Глобальные команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Глобальные команды")
    inner class GlobalCommands {

        @Test
        @DisplayName("Help does not change state")
        fun `Help does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            assertEquals(state, handler.handle(Command.Help, state))
        }

        @Test
        @DisplayName("Quit sets isRunning to false")
        fun `Quit sets isRunning to false`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState(isRunning = true)
            val newState = handler.handle(Command.Quit, state)
            assertEquals(false, newState.isRunning)
        }

        @Test
        @DisplayName("Back is delegated outside CommandHandler")
        fun `Back is no-op in CommandHandler`() = runBlocking {
            val handler = createHandler(emptyMap())
            val states = listOf(
                CliState(currentTaskId = 1),
                CliState(currentTodoTaskId = "some-uuid"),
                CliState(taskListMode = true)
            )

            states.forEach { state ->
                assertEquals(state, handler.handle(Command.Back, state))
            }
        }

        @Test
        @DisplayName("SelectTask sets currentTaskId")
        fun `SelectTask sets currentTaskId`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState(currentTaskId = null)
            val newState = handler.handle(Command.SelectTask(1), state)
            assertEquals(1, newState.currentTaskId)
        }

        @Test
        @DisplayName("SelectTask switches task")
        fun `SelectTask switches task`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState(currentTaskId = 1)
            val newState = handler.handle(Command.SelectTask(2), state)
            assertEquals(2, newState.currentTaskId)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LLM параметры
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("LLM параметры")
    inner class LlmParameters {

        @Test
        @DisplayName("SetTemperature updates executionConfig")
        fun `SetTemperature updates executionConfig`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.SetTemperature(1.5), state)
            assertEquals(1.5, newState.executionConfig.temperature)
        }

        @Test
        @DisplayName("SetMaxTokens updates executionConfig")
        fun `SetMaxTokens updates executionConfig`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.SetMaxTokens(2048), state)
            assertEquals(2048, newState.executionConfig.maxTokens)
        }

        @Test
        @DisplayName("SetStopSequences updates executionConfig")
        fun `SetStopSequences updates executionConfig`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val seq = listOf("END", "STOP")
            val newState = handler.handle(Command.SetStopSequences(seq), state)
            assertEquals(seq, newState.executionConfig.stopSequences)
        }

        @Test
        @DisplayName("ResetParameters resets to defaults")
        fun `ResetParameters resets to defaults`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState(executionConfig = TaskExecutionConfig(temperature = 1.8, maxTokens = 1000))
            val newState = handler.handle(Command.ResetParameters, state)
            assertEquals(TaskExecutionConfig(), newState.executionConfig)
        }

        @Test
        @DisplayName("ShowParameters does not change state")
        fun `ShowParameters does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ShowParameters, state)
            assertEquals(state, newState)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Dialog commands
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dialog commands")
    inner class DialogCommands {

        @Test
        @DisplayName("ListDialogs does not change state")
        fun `ListDialogs does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ListDialogs, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("ShowHistory does not change state")
        fun `ShowHistory does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ShowHistory(), state)
            assertEquals(state, newState)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Compression commands (no-op)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Compression commands")
    inner class CompressionCommands {

        @Test
        @DisplayName("SetCompressionEnabled does not change state")
        fun `SetCompressionEnabled does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.SetCompressionEnabled(true), state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("SetCompressionWindow does not change state")
        fun `SetCompressionWindow does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.SetCompressionWindow(10), state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("SetCompressionBlock does not change state")
        fun `SetCompressionBlock does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.SetCompressionBlock(5), state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("ShowCompressionStatus does not change state")
        fun `ShowCompressionStatus does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ShowCompressionStatus, state)
            assertEquals(state, newState)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy commands (no-op)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Strategy commands")
    inner class StrategyCommands {

        @Test
        @DisplayName("ShowStrategyMenu does not change state")
        fun `ShowStrategyMenu does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ShowStrategyMenu, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("SwitchStrategy does not change state")
        fun `SwitchStrategy does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.SwitchStrategy(1), state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("ShowCurrentStrategy does not change state")
        fun `ShowCurrentStrategy does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ShowCurrentStrategy, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("CreateBranch does not change state")
        fun `CreateBranch does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.CreateBranch("test"), state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("SwitchBranch does not change state")
        fun `SwitchBranch does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.SwitchBranch("test"), state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("ListBranches does not change state")
        fun `ListBranches does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ListBranches, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("CreateCheckpoint does not change state")
        fun `CreateCheckpoint does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.CreateCheckpoint, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("ListCheckpoints does not change state")
        fun `ListCheckpoints does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ListCheckpoints, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("ListFacts does not change state")
        fun `ListFacts does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ListFacts, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("ClearFacts does not change state")
        fun `ClearFacts does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ClearFacts, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("AddFact does not change state")
        fun `AddFact does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.AddFact("key", "value"), state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("RemoveFact does not change state")
        fun `RemoveFact does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.RemoveFact("key"), state)
            assertEquals(state, newState)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Memory commands (Phase 3)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Memory commands")
    inner class MemoryCommands {

        @Test
        @DisplayName("ClearMemory does not change state")
        fun `ClearMemory does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ClearMemory, state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("ShowStatus does not change state")
        fun `ShowStatus does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.ShowStatus, state)
            assertEquals(state, newState)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Unknown and UserInput
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unknown and UserInput")
    inner class UnknownAndUserInput {

        @Test
        @DisplayName("Unknown does not change state")
        fun `Unknown does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.Unknown(":foo"), state)
            assertEquals(state, newState)
        }

        @Test
        @DisplayName("UserInput does not change state")
        fun `UserInput does not change state`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()
            val newState = handler.handle(Command.UserInput("test"), state)
            assertEquals(state, newState)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UserInput — интеграция с executor
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UserInput integration with executor")
    inner class UserInputIntegration {

        @Test
        @DisplayName("UserInput without active task does not call executor")
        fun `UserInput without active task does not call executor`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val handler = createHandler(mapOf(TaskId("1") to executor))
            val state = CliState(currentTaskId = null)
            val (newState, _) = handler.executeUserInput(Command.UserInput("test"), state)
            // При одном executor'е taskId автоматически устанавливается
            assertEquals(1, newState.currentTaskId)
            assertNotNull(executor.lastPrompt)
            assertEquals("test", executor.lastPrompt!!.value)
        }

        @Test
        @DisplayName("UserInput with active task calls executor")
        fun `UserInput with active task calls executor`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val handler = createHandler(mapOf(TaskId("1") to executor))
            val state = CliState(currentTaskId = 1)
            val (newState, result) = handler.executeUserInput(Command.UserInput("test"), state)
            assertEquals(state, newState)
            assertNotNull(result)
            assertIs<TaskResult.Success>(result)
            assertEquals("mock result", result.content)
        }

        @Test
        @DisplayName("executeUserInput returns result")
        fun `executeUserInput returns result`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val handler = createHandler(mapOf(TaskId("1") to executor))
            val state = CliState(currentTaskId = 1)
            val (newState, result) = handler.executeUserInput(Command.UserInput("test"), state)
            assertEquals(state, newState)
            assertNotNull(result)
            assertIs<TaskResult.Success>(result)
            assertEquals("mock result", result.content)
        }

        @Test
        @DisplayName("executeUserInput passes executionConfig")
        fun `executeUserInput passes executionConfig`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val handler = createHandler(mapOf(TaskId("1") to executor))
            val config = TaskExecutionConfig(temperature = 0.5, maxTokens = 100)
            val state = CliState(currentTaskId = 1, executionConfig = config)
            handler.executeUserInput(Command.UserInput("p"), state)
            assertNotNull(executor.lastConfig)
            assertEquals(0.5, executor.lastConfig!!.temperature)
            assertEquals(100, executor.lastConfig!!.maxTokens)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Executor access
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Executor access")
    inner class ExecutorAccess {

        @Test
        @DisplayName("getExecutor returns executor by taskId")
        fun `getExecutor returns executor by taskId`() {
            val executor = MockTaskExecutor(TaskId("1"))
            val handler = createHandler(mapOf(TaskId("1") to executor))
            assertSame(executor, handler.getExecutor(TaskId("1")))
            assertNull(handler.getExecutor(TaskId("999")))
        }

        @Test
        @DisplayName("getAllExecutors returns all executors")
        fun `getAllExecutors returns all executors`() {
            val e1 = MockTaskExecutor(TaskId("1"))
            val e2 = MockTaskExecutor(TaskId("2"))
            val handler = createHandler(mapOf(TaskId("1") to e1, TaskId("2") to e2))
            val all = handler.getAllExecutors()
            assertEquals(2, all.size)
            assertTrue(e1 in all)
            assertTrue(e2 in all)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Task management commands (todo-manager)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Task management commands")
    inner class TaskManagementCommands {

        @Test
        @DisplayName("Todo commands are delegated outside CommandHandler")
        fun `todo commands are no-op in CommandHandler`() = runBlocking {
            val handler = createHandler(emptyMap())
            val state = CliState()

            assertEquals(state, handler.handle(Command.AddTask("Test Task"), state))
            assertEquals(state, handler.handle(Command.ListTasks, state))
            assertEquals(state, handler.handle(Command.EditTask(null, "Updated Title"), state))
            assertEquals(state, handler.handle(Command.DropTask(null), state))
            assertEquals(state, handler.handle(Command.OpenTask(TaskId("task-1")), state))
            assertEquals(state, handler.handle(Command.CloseTask(null), state))
            assertEquals(state, handler.handle(Command.CancelTask(null), state))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Step management commands (Phase 4)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step management commands")
    inner class StepManagementCommands {

        private lateinit var repository: InMemoryTaskRepository
        private lateinit var executor: TodoTaskService
        private lateinit var stepRepository: InMemoryTaskStepRepository
        private lateinit var handler: CommandHandler
        private lateinit var createdTask: Task

        @BeforeEach
        fun setUp() {
            repository = InMemoryTaskRepository()
            executor = TodoTaskService(repository)
            stepRepository = InMemoryTaskStepRepository()
            handler = CommandHandler(
                executors = emptyMap()
            )
        }

        private suspend fun openTask(): CliState {
            createdTask = executor.addTask("Test Task")
            executor.openTask(createdTask.id)
            return CliState(currentTodoTaskId = createdTask.id.value)
        }

        @Test
        @DisplayName("Step commands are delegated outside CommandHandler")
        fun `step commands are no-op in CommandHandler`() = runBlocking {
            val state = openTask()

            assertEquals(state, handler.handle(Command.AddStep("Do something"), state))
            assertEquals(state, handler.handle(Command.ListSteps, state))
            assertEquals(state, handler.handle(Command.CompleteStep("step-1"), state))
            assertTrue(stepRepository.findByTaskId(createdTask.id).isEmpty())
        }
    }
}

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
