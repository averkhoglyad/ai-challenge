package io.averkhogliad.ai.challenge.week0.application.executor

import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.*
import io.averkhogliad.ai.challenge.week0.domain.service.MockLlmPort
import io.averkhogliad.ai.challenge.week0.domain.service.ModelBenchmarkService
import io.averkhogliad.ai.challenge.week0.domain.service.PromptEngineeringService
import io.averkhogliad.ai.challenge.week0.domain.service.TemperatureService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Тесты оркестрации для всех TaskExecutor'ов.
 *
 * Тестируем ТОЛЬКО оркестрацию (делегирование, обработку ошибок,
 * преобразование результатов), а не бизнес-логику — она уже протестирована
 * в domain service тестах.
 */
class TaskExecutorsTest {

    private val mockLlmPort = MockLlmPort()
    private val temperatureService = TemperatureService(mockLlmPort)
    private val promptEngineeringService = PromptEngineeringService(mockLlmPort)
    private val modelBenchmarkService = ModelBenchmarkService(mockLlmPort)

    private val testPrompt = Prompt("test prompt")
    private val testConfig = TaskExecutionConfig()

    @AfterEach
    fun tearDown() {
        mockLlmPort.reset()
    }

    // ---------------------------------------------------------------
    // Task1Executor tests
    // ---------------------------------------------------------------

    @Test
    fun `Task1Executor - metadata`() {
        val executor = Task1Executor(mockLlmPort)
        assertEquals(TaskId(1), executor.taskId)
        assertEquals("Task 1: простой chat-completion (single prompt)", executor.metadata.title)
    }

    @Test
    fun `Task1Executor - execute success`() = runBlocking {
        mockLlmPort.respondWithSuccess("Hello from LLM")
        val executor = Task1Executor(mockLlmPort)
        val result = executor.execute(testPrompt, testConfig)

        assertIs<TaskResult.Success>(result)
        assertEquals("Hello from LLM", result.content)
        assertEquals(1, mockLlmPort.chatCalls.size)
        assertEquals(testPrompt, mockLlmPort.chatCalls.first().first)
    }

    @Test
    fun `Task1Executor - execute error from LLM`() = runBlocking {
        mockLlmPort.respondWithError("API error")
        val executor = Task1Executor(mockLlmPort)
        val result = executor.execute(testPrompt, testConfig)

        assertIs<TaskResult.Error>(result)
        assertEquals("API error", result.message)
    }

    @Test
    fun `Task1Executor - execute exception handling`() = runBlocking {
        mockLlmPort.throwOnNextCall(RuntimeException("Network failure"))
        val executor = Task1Executor(mockLlmPort)
        val result = executor.execute(testPrompt, testConfig)

        assertIs<TaskResult.Error>(result)
        assertTrue(result.message.contains("Task 1 execution failed"))
        assertTrue(result.message.contains("Network failure"))
    }

    // ---------------------------------------------------------------
    // Task2Executor tests
    // ---------------------------------------------------------------

    @Test
    fun `Task2Executor - metadata`() {
        val executor = Task2Executor(mockLlmPort)
        assertEquals(TaskId(2), executor.taskId)
        assertEquals("Task 2: расширенный chat-completion с параметрами", executor.metadata.title)
    }

    @Test
    fun `Task2Executor - execute with valid config`() = runBlocking {
        mockLlmPort.respondWithSuccess("Response with params")
        val executor = Task2Executor(mockLlmPort)
        val result = executor.execute(testPrompt, testConfig)

        assertIs<TaskResult.Success>(result)
        assertEquals("Response with params", result.content)
    }

    @Test
    fun `Task2Executor - Config rejects invalid temperature`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            TaskExecutionConfig(temperature = 3.0)
        }
        assertTrue(exception.message!!.contains("temperature"))
    }

    @Test
    fun `Task2Executor - Config rejects invalid maxTokens`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            TaskExecutionConfig(maxTokens = 0)
        }
        assertTrue(exception.message!!.contains("maxTokens"))
    }

    @Test
    fun `Task2Executor - Config rejects too many stop sequences`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            TaskExecutionConfig(stopSequences = listOf("a", "b", "c", "d", "e"))
        }
        assertTrue(exception.message!!.contains("stopSequences"))
    }

    // ---------------------------------------------------------------
    // Task3Executor tests (DIRECT mode)
    // ---------------------------------------------------------------

    @Test
    fun `Task3Executor - metadata`() {
        val executor = Task3Executor(promptEngineeringService)
        assertEquals(TaskId(3), executor.taskId)
        assertEquals("Task 3: Промпт-инжиниринг с модульными модификаторами", executor.metadata.title)
    }

    @Test
    fun `Task3Executor - execute DIRECT mode`() = runBlocking {
        mockLlmPort.respondWithSuccess("Direct response")
        val executor = Task3Executor(promptEngineeringService)
        val config = TaskExecutionConfig(
            task3 = Task3Config(mode = Task3Mode.DIRECT)
        )
        val result = executor.execute(testPrompt, config)

        assertIs<TaskResult.Success>(result)
        assertEquals("Direct response", result.content)
    }

    @Test
    fun `Task3Executor - execute DIRECT mode with step instruction`() = runBlocking {
        mockLlmPort.respondWithMessagesSuccess("Step by step response")
        val executor = Task3Executor(promptEngineeringService)
        val config = TaskExecutionConfig(
            task3 = Task3Config(
                mode = Task3Mode.DIRECT,
                stepEnabled = true,
                stepInstruction = "STEP_BY_STEP_INSTRUCTION"
            )
        )
        val result = executor.execute(testPrompt, config)

        assertIs<TaskResult.Success>(result)
        assertEquals("Step by step response", result.content)
        assertTrue(mockLlmPort.chatWithMessagesCalls.isNotEmpty())
    }

    @Test
    fun `Task3Executor - execute EXPERTS mode`() = runBlocking {
        mockLlmPort.respondWithMessagesSuccess("Expert opinion")
        val executor = Task3Executor(promptEngineeringService)
        val config = TaskExecutionConfig(
            task3 = Task3Config(
                mode = Task3Mode.EXPERTS,
                experts = listOf("Analyst", "Engineer")
            )
        )
        val result = executor.execute(testPrompt, config)

        assertIs<TaskResult.Success>(result)
        assertTrue(result.content.contains("Analyst"))
        assertTrue(result.content.contains("Engineer"))
        val metadata = result.metadata
        assertEquals("EXPERTS", metadata["mode"])
        assertEquals(2, metadata["expertCount"])
    }

    @Test
    fun `Task3Executor - execute EXPERTS mode with summary`() = runBlocking {
        mockLlmPort.respondWithMessagesSuccess("Expert opinion")
        val executor = Task3Executor(promptEngineeringService)
        val config = TaskExecutionConfig(
            task3 = Task3Config(
                mode = Task3Mode.EXPERTS,
                experts = listOf("Analyst", "Engineer"),
                summary = true
            )
        )
        val result = executor.execute(testPrompt, config)

        assertIs<TaskResult.Success>(result)
        assertTrue(result.content.contains("Итоговое заключение"))
    }

    // ---------------------------------------------------------------
    // Task4Executor tests
    // ---------------------------------------------------------------

    @Test
    fun `Task4Executor - metadata`() {
        val executor = Task4Executor(temperatureService)
        assertEquals(TaskId(4), executor.taskId)
        assertEquals("Task 4: Влияние temperature на генерацию", executor.metadata.title)
    }

    @Test
    fun `Task4Executor - execute with default temperatures`() = runBlocking {
        mockLlmPort.respondWithSuccess("Temperature result")
        val executor = Task4Executor(temperatureService)
        val result = executor.execute(testPrompt, testConfig)

        assertIs<TaskResult.Success>(result)
        // 3 default temperatures should produce 3 results
        assertTrue(result.content.contains("Temperature: 0.0"))
        assertTrue(result.content.contains("Temperature: 0.7"))
        assertTrue(result.content.contains("Temperature: 1.2"))
        assertEquals(3, mockLlmPort.chatCalls.size)
    }

    @Test
    fun `Task4Executor - execute with custom temperatures via Task4Config`() = runBlocking {
        mockLlmPort.respondWithSuccess("Temperature result")
        val executor = Task4Executor(temperatureService)
        val config = TaskExecutionConfig(task4 = Task4Config(temperatures = listOf(0.0, 1.0)))
        val result = executor.execute(testPrompt, config)

        assertIs<TaskResult.Success>(result)
        assertEquals(2, mockLlmPort.chatCalls.size)
    }

    @Test
    fun `Task4Executor - execute with one temperature`() = runBlocking {
        mockLlmPort.respondWithSuccess("Single temp result")
        val executor = Task4Executor(temperatureService)
        val config = TaskExecutionConfig(task4 = Task4Config(temperatures = listOf(0.5)))
        val result = executor.execute(testPrompt, config)

        assertIs<TaskResult.Success>(result)
        assertEquals(1, mockLlmPort.chatCalls.size)
    }

    @Test
    fun `Task4Executor - handles errors in temperature results`() = runBlocking {
        // First call succeeds, second fails
        var callCount = 0
        mockLlmPort.respondWith { prompt, config ->
            callCount++
            if (callCount == 2) {
                TaskResult.Error("Temperature API error")
            } else {
                TaskResult.Success("Temperature result $callCount")
            }
        }

        val executor = Task4Executor(temperatureService)
        val result = executor.execute(testPrompt, testConfig)

        assertIs<TaskResult.Success>(result)
        assertTrue(result.content.contains("Error: Temperature API error"))
        assertEquals(3, mockLlmPort.chatCalls.size)
    }

    // ---------------------------------------------------------------
    // Task5Executor tests
    // ---------------------------------------------------------------

    @Test
    fun `Task5Executor - metadata`() {
        val modelIds = listOf(ModelId("gpt-4"), ModelId("gpt-3.5"))
        val executor = Task5Executor(modelBenchmarkService, modelIds)
        assertEquals(TaskId(5), executor.taskId)
        assertEquals("Task 5: Сравнение производительности моделей", executor.metadata.title)
    }

    @Test
    fun `Task5Executor - execute benchmark`() = runBlocking {
        mockLlmPort.respondWithSuccess("Benchmark response")
        val modelIds = listOf(ModelId("gpt-4"), ModelId("gpt-3.5"))
        val executor = Task5Executor(modelBenchmarkService, modelIds)
        val result = executor.execute(testPrompt, testConfig)

        assertIs<TaskResult.Success>(result)
        assertTrue(result.content.contains("gpt-4"))
        assertTrue(result.content.contains("gpt-3.5"))
        assertTrue(result.content.contains("Benchmark Summary"))
        assertEquals(2, mockLlmPort.chatCalls.size)

        val metadata = result.metadata
        assertEquals(2, metadata["totalModels"])
        assertEquals(2, metadata["successfulModels"])
    }

    @Test
    fun `Task5Executor - execute with models via Task5Config`() = runBlocking {
        mockLlmPort.respondWithSuccess("Benchmark response")
        val executor = Task5Executor(modelBenchmarkService)
        val config = TaskExecutionConfig(
            task5 = Task5Config(modelIds = listOf(ModelId("gpt-4")))
        )
        val result = executor.execute(testPrompt, config)

        assertIs<TaskResult.Success>(result)
        assertEquals(1, mockLlmPort.chatCalls.size)
        assertEquals(1, result.metadata["totalModels"])
    }

    @Test
    fun `Task5Executor - handles model failures`() = runBlocking {
        var callCount = 0
        mockLlmPort.respondWith { prompt, config ->
            callCount++
            if (callCount == 1) {
                TaskResult.Error("Model unavailable")
            } else {
                TaskResult.Success("Benchmark response $callCount")
            }
        }

        val modelIds = listOf(ModelId("gpt-4"), ModelId("gpt-3.5"))
        val executor = Task5Executor(modelBenchmarkService, modelIds)
        val result = executor.execute(testPrompt, testConfig)

        assertIs<TaskResult.Success>(result)
        assertTrue(result.content.contains("Error: Model unavailable"))
        assertEquals(1, result.metadata["failedModels"])
        assertEquals(2, result.metadata["totalModels"])
    }

    // ---------------------------------------------------------------
    // TaskExecutor interface polymorphism test
    // ---------------------------------------------------------------

    @Test
    fun `All executors implement TaskExecutor interface`() {
        val executors: List<TaskExecutor> = listOf(
            Task1Executor(mockLlmPort),
            Task2Executor(mockLlmPort),
            Task3Executor(promptEngineeringService),
            Task4Executor(temperatureService),
            Task5Executor(modelBenchmarkService, listOf(ModelId("gpt-4")))
        )

        // Verify each executor has unique taskId
        val taskIds = executors.map { it.taskId.value }.toSet()
        assertEquals(5, taskIds.size)

        // Verify each executor has metadata
        for (e in executors) {
            assertNotNull(e.metadata)
            assertEquals(e.taskId, e.metadata.id)
            assertTrue(e.metadata.title.isNotEmpty())
        }
    }
}
