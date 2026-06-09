package io.averkhogliad.ai.challenge.week0.domain.service

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Unit-тесты для [TemperatureService].
 */
class TemperatureServiceTest {

    private val mockLlmPort = MockLlmPort()
    private val service = TemperatureService(mockLlmPort)
    private val defaultConfig = TaskExecutionConfig()

    @Test
    fun `benchmarkTemperatures returns results for all temperatures`() = runBlocking {
        mockLlmPort.respondWith { _, config ->
            TaskResult.Success("response at temp=${config.temperature}")
        }
        val temperatures = listOf(0.0, 0.7, 1.2)
        val results = service.benchmarkTemperatures(
            prompt = Prompt("test prompt"),
            temperatures = temperatures,
            config = defaultConfig
        )
        assertEquals(3, results.size)
        results.forEachIndexed { index, result ->
            assertEquals(temperatures[index], result.temperature)
            assertIs<TaskResult.Success>(result.result)
        }
        assertEquals(3, mockLlmPort.chatCalls.size)
    }

    @Test
    fun `benchmarkTemperatures uses different temperature config for each call`() = runBlocking {
        val calledTemperatures = mutableListOf<Double>()
        mockLlmPort.respondWith { _, config ->
            calledTemperatures.add(config.temperature)
            TaskResult.Success("response")
        }
        val temperatures = listOf(0.0, 0.7, 1.2)
        service.benchmarkTemperatures(
            prompt = Prompt("test"),
            temperatures = temperatures,
            config = defaultConfig
        )
        assertEquals(temperatures, calledTemperatures)
    }

    @Test
    fun `benchmarkTemperatures handles errors gracefully`() = runBlocking {
        mockLlmPort.respondWith { _, config ->
            if (config.temperature == 1.2) {
                TaskResult.Error("API error")
            } else {
                TaskResult.Success("response")
            }
        }
        val results = service.benchmarkTemperatures(
            prompt = Prompt("test"),
            temperatures = listOf(0.0, 0.7, 1.2),
            config = defaultConfig
        )
        assertEquals(3, results.size)
        assertIs<TaskResult.Success>(results[0].result)
        assertIs<TaskResult.Success>(results[1].result)
        assertIs<TaskResult.Error>(results[2].result)
    }

    @Test
    fun `benchmarkTemperatures throws on empty list`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            service.benchmarkTemperatures(
                prompt = Prompt("test"),
                temperatures = emptyList(),
                config = defaultConfig
            )
        }
    }

    @Test
    fun `benchmarkTemperatures throws on invalid values`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            service.benchmarkTemperatures(
                prompt = Prompt("test"),
                temperatures = listOf(0.7, 3.0),
                config = defaultConfig
            )
        }
    }

    @Test
    fun `computeStatistics returns correct stats for all success`() {
        val results = listOf(
            TemperatureService.TemperatureResult(0.0, TaskResult.Success("abc")),
            TemperatureService.TemperatureResult(0.7, TaskResult.Success("abcdef")),
            TemperatureService.TemperatureResult(1.2, TaskResult.Success("a"))
        )
        val stats = service.computeStatistics(results)
        assertEquals(3, stats["successfulCount"])
        assertEquals(0, stats["errorCount"])
        assertEquals(10, stats["totalContentLength"])
        assertEquals(3, stats["avgContentLength"])
    }

    @Test
    fun `computeStatistics returns correct stats with errors`() {
        val results = listOf(
            TemperatureService.TemperatureResult(0.0, TaskResult.Success("abc")),
            TemperatureService.TemperatureResult(0.7, TaskResult.Error("fail")),
            TemperatureService.TemperatureResult(1.2, TaskResult.Success("abcdef"))
        )
        val stats = service.computeStatistics(results)
        assertEquals(2, stats["successfulCount"])
        assertEquals(1, stats["errorCount"])
        assertEquals(9, stats["totalContentLength"])
        assertEquals(4, stats["avgContentLength"])
    }

    @Test
    fun `computeStatistics handles empty results`() {
        val stats = service.computeStatistics(emptyList())
        assertEquals(0, stats["successfulCount"])
        assertEquals(0, stats["errorCount"])
        assertEquals(0, stats["avgContentLength"])
    }

    @Test
    fun `describeTemperature returns correct descriptions`() {
        assertEquals("максимальная детерминированность", TemperatureService.describeTemperature(0.0))
        assertEquals("высокая детерминированность", TemperatureService.describeTemperature(0.2))
        assertEquals("умеренная случайность", TemperatureService.describeTemperature(0.5))
        assertEquals("сбалансированный режим", TemperatureService.describeTemperature(0.7))
        assertEquals("повышенная креативность", TemperatureService.describeTemperature(1.2))
        assertEquals("максимальная креативность", TemperatureService.describeTemperature(2.0))
    }
}
