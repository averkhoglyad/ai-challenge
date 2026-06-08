package io.averkhogliad.ai.challenge.week0.cli

import io.averkhogliad.ai.challenge.week0.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week0.cli.commands.Command
import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.Task3Config
import io.averkhogliad.ai.challenge.week0.domain.config.Task3Mode
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class CommandHandlerTest {

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

    // ═══════════════════════════════════════════════════
    // Глобальные команды
    // ═══════════════════════════════════════════════════

    @Test
    fun `Help does not change state`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState()
        assertEquals(state, handler.handle(Command.Help, state))
    }

    @Test
    fun `Quit sets isRunning to false`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState(isRunning = true)
        assertFalse(handler.handle(Command.Quit, state).isRunning)
    }

    @Test
    fun `Back resets currentTaskId`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState(currentTaskId = 3)
        assertNull(handler.handle(Command.Back, state).currentTaskId)
    }

    @Test
    fun `SelectTask sets currentTaskId`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState(currentTaskId = null)
        assertEquals(5, handler.handle(Command.SelectTask(5), state).currentTaskId)
    }

    @Test
    fun `SelectTask switches task`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState(currentTaskId = 1)
        assertEquals(3, handler.handle(Command.SelectTask(3), state).currentTaskId)
    }

    // ═══════════════════════════════════════════════════
    // LLM параметры
    // ═══════════════════════════════════════════════════

    @Test
    fun `SetTemperature updates executionConfig`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState()
        assertEquals(1.5, handler.handle(Command.SetTemperature(1.5), state).executionConfig.temperature)
    }

    @Test
    fun `SetMaxTokens updates executionConfig`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState()
        assertEquals(2048, handler.handle(Command.SetMaxTokens(2048), state).executionConfig.maxTokens)
    }

    @Test
    fun `SetStopSequences updates executionConfig`() = runBlocking {
        val handler = createHandler(emptyMap())
        val seq = listOf("END", "STOP")
        assertEquals(seq, handler.handle(Command.SetStopSequences(seq), CliState()).executionConfig.stopSequences)
    }

    @Test
    fun `ResetParameters resets to defaults`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState(executionConfig = TaskExecutionConfig(temperature = 1.8, maxTokens = 1000))
        assertEquals(TaskExecutionConfig(), handler.handle(Command.ResetParameters, state).executionConfig)
    }

    @Test
    fun `ShowParameters does not change state`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState()
        assertEquals(state, handler.handle(Command.ShowParameters, state))
    }

    // ═══════════════════════════════════════════════════
    // Task3 команды
    // ═══════════════════════════════════════════════════

    @Test
    fun `SetMode updates task3Mode`() = runBlocking {
        val handler = createHandler(emptyMap())
        assertEquals(
            Task3Mode.EXPERTS,
            handler.handle(Command.SetMode(Task3Mode.EXPERTS), CliState()).executionConfig.task3.mode
        )
    }

    @Test
    fun `SetStep updates task3Step`() = runBlocking {
        val handler = createHandler(emptyMap())
        val result = handler.handle(Command.SetStep(true), CliState())
        assertTrue(result.executionConfig.task3.stepEnabled)
        assertEquals(Task3Config.DEFAULT_STEP_INSTRUCTION, result.executionConfig.task3.stepInstruction)
    }

    @Test
    fun `SetStep off disables task3Step`() = runBlocking {
        val handler = createHandler(emptyMap())
        val result = handler.handle(Command.SetStep(false), CliState())
        assertFalse(result.executionConfig.task3.stepEnabled)
        assertNull(result.executionConfig.task3.stepInstruction)
    }

    @Test
    fun `SetMeta updates task3Meta`() = runBlocking {
        val handler = createHandler(emptyMap())
        val result = handler.handle(Command.SetMeta(true), CliState())
        assertTrue(result.executionConfig.task3.metaEnabled)
    }

    @Test
    fun `SetRole updates task3Role`() = runBlocking {
        val handler = createHandler(emptyMap())
        assertEquals("Senior Dev", handler.handle(Command.SetRole("Senior Dev"), CliState()).executionConfig.task3.role)
    }

    @Test
    fun `SetExperts updates task3Experts`() = runBlocking {
        val handler = createHandler(emptyMap())
        val experts = listOf("Architect", "DevOps")
        assertEquals(experts, handler.handle(Command.SetExperts(experts), CliState()).executionConfig.task3.experts)
    }

    @Test
    fun `ToggleSummary updates task3Summary`() = runBlocking {
        val handler = createHandler(emptyMap())
        assertEquals(true, handler.handle(Command.ToggleSummary(true), CliState()).executionConfig.task3.summary)
    }

    @Test
    fun `ShowConfig does not change state`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState()
        assertEquals(state, handler.handle(Command.ShowConfig, state))
    }

    // ═══════════════════════════════════════════════════
    // Task5 команды
    // ═══════════════════════════════════════════════════

    @Test
    fun `SetModels updates task5SelectedModels`() = runBlocking {
        val handler = createHandler(emptyMap())
        val models = listOf(1, 3, 5)
        assertEquals(models, handler.handle(Command.SetModels(models), CliState()).task5SelectedModels)
    }

    @Test
    fun `ShowModels does not change state`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState()
        assertEquals(state, handler.handle(Command.ShowModels, state))
    }

    // ═══════════════════════════════════════════════════
    // Unknown
    // ═══════════════════════════════════════════════════

    @Test
    fun `Unknown does not change state`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState()
        assertEquals(state, handler.handle(Command.Unknown(":foo"), state))
    }

    // ═══════════════════════════════════════════════════
    // UserInput — интеграция с executor
    // ═══════════════════════════════════════════════════

    @Test
    fun `UserInput without active task does not change state`() = runBlocking {
        val executor = MockTaskExecutor(TaskId(1))
        val handler = createHandler(mapOf(TaskId(1) to executor))
        val state = CliState(currentTaskId = null)
        assertEquals(state, handler.handle(Command.UserInput("test"), state))
        assertNull(executor.lastPrompt)
    }

    @Test
    fun `UserInput with active task calls executor`() = runBlocking {
        val executor = MockTaskExecutor(TaskId(1))
        val handler = createHandler(mapOf(TaskId(1) to executor))
        val state = CliState(currentTaskId = 1)
        handler.executeUserInput(Command.UserInput("test"), state)
        assertNotNull(executor.lastPrompt)
        assertEquals("test", executor.lastPrompt!!.value)
    }

    @Test
    fun `executeUserInput returns result`() = runBlocking {
        val executor = MockTaskExecutor(TaskId(1))
        val handler = createHandler(mapOf(TaskId(1) to executor))
        val state = CliState(currentTaskId = 1)
        val (newState, result) = handler.executeUserInput(Command.UserInput("test"), state)
        assertEquals(state, newState)
        assertNotNull(result)
        assertIs<TaskResult.Success>(result)
        assertEquals("mock result", (result as TaskResult.Success).content)
    }

    @Test
    fun `executeUserInput without active task returns null`() = runBlocking {
        val handler = createHandler(emptyMap())
        val state = CliState(currentTaskId = null)
        val (newState, result) = handler.executeUserInput(Command.UserInput("test"), state)
        assertEquals(state, newState)
        assertNull(result)
    }

    @Test
    fun `executeUserInput passes executionConfig`() = runBlocking {
        val executor = MockTaskExecutor(TaskId(2))
        val handler = createHandler(mapOf(TaskId(2) to executor))
        val config = TaskExecutionConfig(temperature = 0.5, maxTokens = 100)
        val state = CliState(currentTaskId = 2, executionConfig = config)
        handler.executeUserInput(Command.UserInput("p"), state)
        assertNotNull(executor.lastConfig)
        assertEquals(0.5, executor.lastConfig!!.temperature)
        assertEquals(100, executor.lastConfig!!.maxTokens)
    }

    // ═══════════════════════════════════════════════════
    // Вспомогательные методы
    // ═══════════════════════════════════════════════════

    @Test
    fun `getExecutor returns executor by taskId`() {
        val executor = MockTaskExecutor(TaskId(3))
        val handler = createHandler(mapOf(TaskId(3) to executor))
        assertSame(executor, handler.getExecutor(TaskId(3)))
        assertNull(handler.getExecutor(TaskId(999)))
    }

    @Test
    fun `getAllExecutors returns all executors`() {
        val e1 = MockTaskExecutor(TaskId(1))
        val e2 = MockTaskExecutor(TaskId(2))
        val handler = createHandler(mapOf(TaskId(1) to e1, TaskId(2) to e2))
        val all = handler.getAllExecutors()
        assertEquals(2, all.size)
        assertTrue(e1 in all)
        assertTrue(e2 in all)
    }

    // ═══════════════════════════════════════════════════
    // Цепочка команд
    // ═══════════════════════════════════════════════════

    @Test
    fun `chain SelectTask to SetTemperature to UserInput to Back`() = runBlocking {
        val executor = MockTaskExecutor(TaskId(1))
        val handler = createHandler(mapOf(TaskId(1) to executor))
        var state = CliState()

        state = handler.handle(Command.SelectTask(1), state)
        assertEquals(1, state.currentTaskId)

        state = handler.handle(Command.SetTemperature(1.2), state)
        assertEquals(1.2, state.executionConfig.temperature)

        val (newState, result) = handler.executeUserInput(Command.UserInput("hello"), state)
        state = newState
        assertEquals(1, state.currentTaskId)
        assertNotNull(executor.lastPrompt)
        assertEquals(1.2, executor.lastConfig!!.temperature)

        state = handler.handle(Command.Back, state)
        assertNull(state.currentTaskId)
    }
}
