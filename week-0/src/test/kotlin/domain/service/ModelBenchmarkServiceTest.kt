package io.averkhogliad.ai.challenge.week0.domain.service

import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.BenchmarkConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Unit-тесты для [ModelBenchmarkService].
 */
class ModelBenchmarkServiceTest {

    private val mockLlmPort = MockLlmPort()
    private val service = ModelBenchmarkService(mockLlmPort)

    private val modelIds = listOf(
        ModelId("gpt-4"),
        ModelId("gpt-3.5-turbo"),
        ModelId("claude-3")
    )

    private val defaultConfig = BenchmarkConfig(
        modelIds = modelIds,
        prompt = Prompt("test prompt")
    )

    @Test
    fun `benchmarkModels returns results for all models`() = runBlocking {
        mockLlmPort.respondWith { _, config ->
            TaskResult.Success("response from ${config.modelId?.value}")
        }
        val results = service.benchmarkModels(defaultConfig)
        assertEquals(3, results.size)
        assertEquals(modelIds, results.map { it.modelId })
        assertTrue(results.all { it.result is TaskResult.Success })
    }

    @Test
    fun `benchmarkModels measures duration for each model`() = runBlocking {
        mockLlmPort.respondWithSuccess("response")
        val results = service.benchmarkModels(defaultConfig)
        assertEquals(3, results.size)
        results.forEach {
            assertTrue(it.durationMs >= 0)
            assertTrue(it.durationMs < 5000)
        }
    }

    @Test
    fun `benchmarkModels creates correct TaskExecutionConfig for each model`() = runBlocking {
        val calledModelIds: MutableList<ModelId> = mutableListOf()
        val calledMaxTokens: MutableList<Int> = mutableListOf()
        mockLlmPort.respondWith { _, config ->
            calledModelIds.add(config.modelId!!)
            calledMaxTokens.add(config.maxTokens)
            TaskResult.Success("response")
        }
        val config = BenchmarkConfig(
            modelIds = modelIds,
            maxTokens = 1000,
            prompt = Prompt("test prompt")
        )
        service.benchmarkModels(config)
        assertEquals(modelIds, calledModelIds)
        assertTrue(calledMaxTokens.all { it == 1000 })
    }

    @Test
    fun `benchmarkModels handles errors gracefully`() = runBlocking {
        mockLlmPort.respondWith { _, config ->
            if (config.modelId?.value == "gpt-3.5-turbo") {
                TaskResult.Error("model unavailable")
            } else {
                TaskResult.Success("response")
            }
        }
        val results = service.benchmarkModels(defaultConfig)
        assertEquals(3, results.size)
        assertIs<TaskResult.Success>(results[0].result)
        assertIs<TaskResult.Error>(results[1].result)
        assertIs<TaskResult.Success>(results[2].result)
    }

    @Test
    fun `benchmarkModels with single model returns single result`() = runBlocking {
        mockLlmPort.respondWithSuccess("response")
        val config = BenchmarkConfig(
            modelIds = listOf(ModelId("gpt-4")),
            prompt = Prompt("test")
        )
        val results = service.benchmarkModels(config)
        assertEquals(1, results.size)
        assertEquals(ModelId("gpt-4"), results[0].modelId)
    }

    @Test
    fun `computeSummary returns correct stats for all success`() {
        val results = listOf(
            ModelBenchmarkService.ModelBenchmarkResult(ModelId("gpt-4"), TaskResult.Success("a"), 100),
            ModelBenchmarkService.ModelBenchmarkResult(ModelId("gpt-3.5"), TaskResult.Success("b"), 50),
            ModelBenchmarkService.ModelBenchmarkResult(ModelId("claude"), TaskResult.Success("c"), 150)
        )
        val summary = service.computeSummary(results)
        assertEquals(3, summary.totalModels)
        assertEquals(3, summary.successfulModels)
        assertEquals(0, summary.failedModels)
        assertEquals(ModelId("gpt-3.5"), summary.fastestModel?.first)
        assertEquals(50, summary.fastestModel?.second)
        assertEquals(ModelId("claude"), summary.slowestModel?.first)
        assertEquals(150, summary.slowestModel?.second)
        assertEquals(100, summary.avgDurationMs)
    }

    @Test
    fun `computeSummary returns correct stats with errors`() {
        val results = listOf(
            ModelBenchmarkService.ModelBenchmarkResult(ModelId("gpt-4"), TaskResult.Success("a"), 100),
            ModelBenchmarkService.ModelBenchmarkResult(ModelId("gpt-3.5"), TaskResult.Error("fail"), 0),
            ModelBenchmarkService.ModelBenchmarkResult(ModelId("claude"), TaskResult.Success("c"), 200)
        )
        val summary = service.computeSummary(results)
        assertEquals(3, summary.totalModels)
        assertEquals(2, summary.successfulModels)
        assertEquals(1, summary.failedModels)
        assertEquals(ModelId("gpt-4"), summary.fastestModel?.first)
        assertEquals(150, summary.avgDurationMs)
    }

    @Test
    fun `computeSummary handles all errors`() {
        val results = listOf(
            ModelBenchmarkService.ModelBenchmarkResult(ModelId("gpt-4"), TaskResult.Error("fail1"), 0),
            ModelBenchmarkService.ModelBenchmarkResult(ModelId("gpt-3.5"), TaskResult.Error("fail2"), 0)
        )
        val summary = service.computeSummary(results)
        assertEquals(2, summary.totalModels)
        assertEquals(0, summary.successfulModels)
        assertEquals(2, summary.failedModels)
        assertNull(summary.fastestModel)
        assertNull(summary.slowestModel)
        assertEquals(0, summary.avgDurationMs)
    }
}
