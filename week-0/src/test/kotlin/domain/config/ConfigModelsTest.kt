package io.averkhogliad.ai.challenge.week0.domain.config

import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.Prompt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тесты для immutable конфигурационных моделей.
 *
 * Покрывают:
 * - Валидацию всех инвариантов
 * - Значения по умолчанию
 * - copy() с валидацией
 */
class ConfigModelsTest {

    // ============= LlmConfig =============

    @Test
    fun `LlmConfig should be created with valid values`() {
        val config = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        assertEquals("https://api.openai.com", config.baseUrl)
        assertEquals("sk-12345", config.apiKey)
        assertEquals(ModelId("gpt-4"), config.defaultModelId)
        assertEquals(0.7, config.defaultTemperature)
        assertEquals(500, config.defaultMaxTokens)
        assertEquals(60, config.timeoutSeconds)
    }

    @Test
    fun `LlmConfig should use default values`() {
        val config = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        assertEquals(0.7, config.defaultTemperature)
        assertEquals(500, config.defaultMaxTokens)
        assertEquals(60, config.timeoutSeconds)
    }

    @Test
    fun `LlmConfig should override defaults`() {
        val config = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4"),
            defaultTemperature = 0.5,
            defaultMaxTokens = 1000,
            timeoutSeconds = 120
        )
        assertEquals(0.5, config.defaultTemperature)
        assertEquals(1000, config.defaultMaxTokens)
        assertEquals(120, config.timeoutSeconds)
    }

    @Test
    fun `LlmConfig should reject blank baseUrl`() {
        assertThrows<IllegalArgumentException> {
            LlmConfig(
                baseUrl = "   ",
                apiKey = "sk-12345",
                defaultModelId = ModelId("gpt-4")
            )
        }
    }

    @Test
    fun `LlmConfig should reject blank apiKey`() {
        assertThrows<IllegalArgumentException> {
            LlmConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "",
                defaultModelId = ModelId("gpt-4")
            )
        }
    }

    @Test
    fun `LlmConfig should reject temperature below 0`() {
        assertThrows<IllegalArgumentException> {
            LlmConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "sk-12345",
                defaultModelId = ModelId("gpt-4"),
                defaultTemperature = -0.1
            )
        }
    }

    @Test
    fun `LlmConfig should reject temperature above 2`() {
        assertThrows<IllegalArgumentException> {
            LlmConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "sk-12345",
                defaultModelId = ModelId("gpt-4"),
                defaultTemperature = 2.1
            )
        }
    }

    @Test
    fun `LlmConfig should accept boundary temperatures`() {
        LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4"),
            defaultTemperature = 0.0
        )
        LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4"),
            defaultTemperature = 2.0
        )
    }

    @Test
    fun `LlmConfig should reject maxTokens below 1`() {
        assertThrows<IllegalArgumentException> {
            LlmConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "sk-12345",
                defaultModelId = ModelId("gpt-4"),
                defaultMaxTokens = 0
            )
        }
    }

    @Test
    fun `LlmConfig should reject maxTokens above 128000`() {
        assertThrows<IllegalArgumentException> {
            LlmConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "sk-12345",
                defaultModelId = ModelId("gpt-4"),
                defaultMaxTokens = 128001
            )
        }
    }

    @Test
    fun `LlmConfig should accept boundary maxTokens`() {
        LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4"),
            defaultMaxTokens = 1
        )
        LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4"),
            defaultMaxTokens = 128000
        )
    }

    @Test
    fun `LlmConfig should reject non-positive timeoutSeconds`() {
        assertThrows<IllegalArgumentException> {
            LlmConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "sk-12345",
                defaultModelId = ModelId("gpt-4"),
                timeoutSeconds = 0
            )
        }
        assertThrows<IllegalArgumentException> {
            LlmConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "sk-12345",
                defaultModelId = ModelId("gpt-4"),
                timeoutSeconds = -1
            )
        }
    }

    @Test
    fun `LlmConfig copy should preserve validation`() {
        val config = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        val copied = config.copy(defaultTemperature = 1.5)
        assertEquals(1.5, copied.defaultTemperature)
        assertEquals(500, copied.defaultMaxTokens)
    }

    @Test
    fun `LlmConfig copy with invalid value should fail`() {
        val config = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        assertThrows<IllegalArgumentException> {
            config.copy(defaultTemperature = 3.0)
        }
    }

    @Test
    fun `LlmConfig toString should mask apiKey`() {
        val config = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-secret-12345",
            defaultModelId = ModelId("gpt-4")
        )
        val str = config.toString()
        assertFalse(str.contains("sk-secret-12345"), "toString should mask apiKey")
        assertTrue(str.contains("***"), "toString should contain masking")
        assertTrue(str.contains("https://api.openai.com"), "toString should contain baseUrl")
    }

    // ============= Task4Config =============

    @Test
    fun `Task4Config should be created with default values`() {
        val config = Task4Config()
        assertEquals(listOf(0.0, 0.7, 1.2), config.temperatures)
    }

    @Test
    fun `Task4Config should be created with custom temperatures`() {
        val config = Task4Config(temperatures = listOf(0.0, 1.0))
        assertEquals(listOf(0.0, 1.0), config.temperatures)
    }

    @Test
    fun `Task4Config should reject empty temperatures`() {
        assertThrows<IllegalArgumentException> {
            Task4Config(temperatures = emptyList())
        }
    }

    @Test
    fun `Task4Config should reject temperature below 0`() {
        assertThrows<IllegalArgumentException> {
            Task4Config(temperatures = listOf(-0.1, 0.7))
        }
    }

    @Test
    fun `Task4Config should reject temperature above 2`() {
        assertThrows<IllegalArgumentException> {
            Task4Config(temperatures = listOf(0.7, 2.1))
        }
    }

    @Test
    fun `Task4Config should accept boundary temperatures`() {
        Task4Config(temperatures = listOf(0.0))
        Task4Config(temperatures = listOf(2.0))
    }

    @Test
    fun `Task4Config copy should preserve validation`() {
        val config = Task4Config()
        val copied = config.copy(temperatures = listOf(0.5, 1.5))
        assertEquals(listOf(0.5, 1.5), copied.temperatures)
    }

    @Test
    fun `Task4Config copy with invalid value should fail`() {
        val config = Task4Config()
        assertThrows<IllegalArgumentException> {
            config.copy(temperatures = emptyList())
        }
    }

    // ============= Task5Config =============

    @Test
    fun `Task5Config should be created with default values`() {
        val config = Task5Config()
        assertEquals(emptyList(), config.modelIds)
        assertTrue(config.isEmpty)
        assertFalse(config.isNotEmpty)
    }

    @Test
    fun `Task5Config should be created with custom modelIds`() {
        val modelIds = listOf(ModelId("gpt-4"), ModelId("gpt-4o-mini"))
        val config = Task5Config(modelIds = modelIds)
        assertEquals(modelIds, config.modelIds)
        assertFalse(config.isEmpty)
        assertTrue(config.isNotEmpty)
    }

    @Test
    fun `Task5Config copy should work`() {
        val config = Task5Config()
        val modelIds = listOf(ModelId("gpt-4"))
        val copied = config.copy(modelIds = modelIds)
        assertEquals(modelIds, copied.modelIds)
    }

    // ============= TaskExecutionConfig =============

    @Test
    fun `TaskExecutionConfig should be created with valid values`() {
        val config = TaskExecutionConfig(
            temperature = 0.5,
            maxTokens = 200,
            stopSequences = listOf("END"),
            modelId = ModelId("gpt-4")
        )
        assertEquals(0.5, config.temperature)
        assertEquals(200, config.maxTokens)
        assertEquals(listOf("END"), config.stopSequences)
        assertEquals(ModelId("gpt-4"), config.modelId)
    }

    @Test
    fun `TaskExecutionConfig should use default values`() {
        val config = TaskExecutionConfig()
        assertEquals(0.7, config.temperature)
        assertEquals(500, config.maxTokens)
        assertEquals(emptyList(), config.stopSequences)
        assertEquals(null, config.modelId)
        assertEquals(Task4Config(), config.task4)
        assertEquals(Task5Config(), config.task5)
    }

    @Test
    fun `TaskExecutionConfig should reject temperature out of range`() {
        assertThrows<IllegalArgumentException> {
            TaskExecutionConfig(temperature = -0.1)
        }
        assertThrows<IllegalArgumentException> {
            TaskExecutionConfig(temperature = 2.1)
        }
    }

    @Test
    fun `TaskExecutionConfig should reject maxTokens out of range`() {
        assertThrows<IllegalArgumentException> {
            TaskExecutionConfig(maxTokens = 0)
        }
        assertThrows<IllegalArgumentException> {
            TaskExecutionConfig(maxTokens = 128001)
        }
    }

    @Test
    fun `TaskExecutionConfig should reject more than 4 stop sequences`() {
        assertThrows<IllegalArgumentException> {
            TaskExecutionConfig(stopSequences = listOf("a", "b", "c", "d", "e"))
        }
    }

    @Test
    fun `TaskExecutionConfig should accept exactly 4 stop sequences`() {
        val config = TaskExecutionConfig(stopSequences = listOf("a", "b", "c", "d"))
        assertEquals(4, config.stopSequences.size)
    }

    @Test
    fun `TaskExecutionConfig copy should preserve validation`() {
        val config = TaskExecutionConfig()
        val copied = config.copy(temperature = 0.0, maxTokens = 100)
        assertEquals(0.0, copied.temperature)
        assertEquals(100, copied.maxTokens)
    }

    @Test
    fun `TaskExecutionConfig copy with invalid value should fail`() {
        val config = TaskExecutionConfig()
        assertThrows<IllegalArgumentException> {
            config.copy(stopSequences = listOf("a", "b", "c", "d", "e", "f"))
        }
    }

    // ============= BenchmarkConfig =============

    @Test
    fun `BenchmarkConfig should be created with valid values`() {
        val config = BenchmarkConfig(
            modelIds = listOf(ModelId("gpt-4"), ModelId("gpt-4o-mini")),
            temperatures = listOf(0.0, 0.7),
            maxTokens = 300,
            prompt = Prompt("Hello")
        )
        assertEquals(2, config.modelIds.size)
        assertEquals(listOf(0.0, 0.7), config.temperatures)
        assertEquals(300, config.maxTokens)
        assertEquals(Prompt("Hello"), config.prompt)
    }

    @Test
    fun `BenchmarkConfig should use default temperatures`() {
        val config = BenchmarkConfig(
            modelIds = listOf(ModelId("gpt-4")),
            prompt = Prompt("Hello")
        )
        assertEquals(listOf(0.0, 0.7, 1.2), config.temperatures)
        assertEquals(500, config.maxTokens)
    }

    @Test
    fun `BenchmarkConfig should reject empty modelIds`() {
        assertThrows<IllegalArgumentException> {
            BenchmarkConfig(
                modelIds = emptyList(),
                prompt = Prompt("Hello")
            )
        }
    }

    @Test
    fun `BenchmarkConfig should reject empty temperatures`() {
        assertThrows<IllegalArgumentException> {
            BenchmarkConfig(
                modelIds = listOf(ModelId("gpt-4")),
                temperatures = emptyList(),
                prompt = Prompt("Hello")
            )
        }
    }

    @Test
    fun `BenchmarkConfig should reject invalid temperatures`() {
        assertThrows<IllegalArgumentException> {
            BenchmarkConfig(
                modelIds = listOf(ModelId("gpt-4")),
                temperatures = listOf(0.0, -0.1, 1.0),
                prompt = Prompt("Hello")
            )
        }
        assertThrows<IllegalArgumentException> {
            BenchmarkConfig(
                modelIds = listOf(ModelId("gpt-4")),
                temperatures = listOf(0.0, 2.1),
                prompt = Prompt("Hello")
            )
        }
    }

    @Test
    fun `BenchmarkConfig should reject maxTokens out of range`() {
        assertThrows<IllegalArgumentException> {
            BenchmarkConfig(
                modelIds = listOf(ModelId("gpt-4")),
                maxTokens = 0,
                prompt = Prompt("Hello")
            )
        }
        assertThrows<IllegalArgumentException> {
            BenchmarkConfig(
                modelIds = listOf(ModelId("gpt-4")),
                maxTokens = 128001,
                prompt = Prompt("Hello")
            )
        }
    }

    @Test
    fun `BenchmarkConfig copy should preserve validation`() {
        val config = BenchmarkConfig(
            modelIds = listOf(ModelId("gpt-4")),
            prompt = Prompt("Hello")
        )
        val copied = config.copy(maxTokens = 200)
        assertEquals(200, copied.maxTokens)
        assertEquals(listOf(0.0, 0.7, 1.2), copied.temperatures)
    }

    @Test
    fun `BenchmarkConfig copy with invalid value should fail`() {
        val config = BenchmarkConfig(
            modelIds = listOf(ModelId("gpt-4")),
            prompt = Prompt("Hello")
        )
        assertThrows<IllegalArgumentException> {
            config.copy(modelIds = emptyList())
        }
    }

    // ============= AppConfig =============

    @Test
    fun `AppConfig should be created with valid values`() {
        val llmConfig = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        val appConfig = AppConfig(
            llm = llmConfig,
            replTimeoutSeconds = 600
        )
        assertEquals(llmConfig, appConfig.llm)
        assertEquals(TaskExecutionConfig(), appConfig.defaultExecution)
        assertEquals(600, appConfig.replTimeoutSeconds)
    }

    @Test
    fun `AppConfig should use default values`() {
        val llmConfig = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        val appConfig = AppConfig(llm = llmConfig)
        assertEquals(TaskExecutionConfig(), appConfig.defaultExecution)
        assertEquals(300, appConfig.replTimeoutSeconds)
    }

    @Test
    fun `AppConfig should reject non-positive replTimeoutSeconds`() {
        val llmConfig = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        assertThrows<IllegalArgumentException> {
            AppConfig(llm = llmConfig, replTimeoutSeconds = 0)
        }
        assertThrows<IllegalArgumentException> {
            AppConfig(llm = llmConfig, replTimeoutSeconds = -5)
        }
    }

    @Test
    fun `AppConfig copy should preserve validation`() {
        val llmConfig = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        val appConfig = AppConfig(llm = llmConfig)
        val copied = appConfig.copy(replTimeoutSeconds = 900)
        assertEquals(900, copied.replTimeoutSeconds)
    }

    @Test
    fun `AppConfig copy with invalid value should fail`() {
        val llmConfig = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-12345",
            defaultModelId = ModelId("gpt-4")
        )
        val appConfig = AppConfig(llm = llmConfig)
        assertThrows<IllegalArgumentException> {
            appConfig.copy(replTimeoutSeconds = 0)
        }
    }
}
