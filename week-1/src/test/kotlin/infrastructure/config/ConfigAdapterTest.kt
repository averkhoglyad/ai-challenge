package io.averkhogliad.ai.challenge.week1.infrastructure.config

import io.averkhogliad.ai.challenge.llm.config.TestConfig
import io.averkhogliad.ai.challenge.week1.domain.ModelId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ConfigAdapterTest {

    private fun minimalTestConfig(
        baseUrl: String = "https://api.example.com",
        apiKey: String = "test-key",
        defaultModel: String = "openai/gpt-4o-mini"
    ): TestConfig = TestConfig(
        mapOf(
            "llm.base-url" to baseUrl,
            "llm.api-key" to apiKey,
            "llm.default-model" to defaultModel
        )
    )

    // ──── Positive tests: loadAppConfig() ────

    @Test
    fun `should load AppConfig with all default values`() {
        // given
        val config = minimalTestConfig()
        val adapter = ConfigAdapter(config)

        // when
        val appConfig = adapter.loadAppConfig()

        // then
        assertEquals("https://api.example.com", appConfig.llm.baseUrl)
        assertEquals("test-key", appConfig.llm.apiKey)
        assertEquals(ModelId("openai/gpt-4o-mini"), appConfig.llm.defaultModelId)
        assertEquals(0.7, appConfig.llm.defaultTemperature)
        assertEquals(500, appConfig.llm.defaultMaxTokens)
        assertEquals(60, appConfig.llm.timeoutSeconds)
        assertEquals(0.7, appConfig.defaultExecution.temperature)
        assertEquals(500, appConfig.defaultExecution.maxTokens)
        assertEquals(300, appConfig.replTimeoutSeconds)
    }

    @Test
    fun `should load LlmConfig with custom values`() {
        // given
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://custom.api.com",
                "llm.api-key" to "custom-key",
                "llm.default-model" to "deepseek/deepseek-v4-flash",
                "llm.default-temperature" to "1.5",
                "llm.default-max-tokens" to "2000",
                "llm.timeout-seconds" to "120"
            )
        )
        val adapter = ConfigAdapter(config)

        // when
        val llmConfig = adapter.loadAppConfig().llm

        // then
        assertEquals("https://custom.api.com", llmConfig.baseUrl)
        assertEquals("custom-key", llmConfig.apiKey)
        assertEquals(ModelId("deepseek/deepseek-v4-flash"), llmConfig.defaultModelId)
        assertEquals(1.5, llmConfig.defaultTemperature)
        assertEquals(2000, llmConfig.defaultMaxTokens)
        assertEquals(120, llmConfig.timeoutSeconds)
    }

    @Test
    fun `should load TaskExecutionConfig with custom values`() {
        // given
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "execution.temperature" to "0.3",
                "execution.max-tokens" to "1024"
            )
        )
        val adapter = ConfigAdapter(config)

        // when
        val execConfig = adapter.loadAppConfig().defaultExecution

        // then
        assertEquals(0.3, execConfig.temperature)
        assertEquals(1024, execConfig.maxTokens)
    }

    @Test
    fun `should load custom repl timeout`() {
        // given
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "app.repl-timeout-seconds" to "600"
            )
        )
        val adapter = ConfigAdapter(config)

        // when
        val appConfig = adapter.loadAppConfig()

        // then
        assertEquals(600, appConfig.replTimeoutSeconds)
    }

    // ──── Default value tests ────

    @Test
    fun `should use default temperature when not specified`() {
        val config = minimalTestConfig()
        val adapter = ConfigAdapter(config)

        val llmConfig = adapter.loadAppConfig().llm

        assertEquals(0.7, llmConfig.defaultTemperature)
    }

    @Test
    fun `should use default maxTokens when not specified`() {
        val config = minimalTestConfig()
        val adapter = ConfigAdapter(config)

        val llmConfig = adapter.loadAppConfig().llm

        assertEquals(500, llmConfig.defaultMaxTokens)
    }

    @Test
    fun `should use default timeout when not specified`() {
        val config = minimalTestConfig()
        val adapter = ConfigAdapter(config)

        val llmConfig = adapter.loadAppConfig().llm

        assertEquals(60, llmConfig.timeoutSeconds)
    }

    @Test
    fun `should use default execution config when not specified`() {
        val config = minimalTestConfig()
        val adapter = ConfigAdapter(config)

        val execConfig = adapter.loadAppConfig().defaultExecution

        assertEquals(0.7, execConfig.temperature)
        assertEquals(500, execConfig.maxTokens)
    }

    @Test
    fun `should use default repl timeout when not specified`() {
        val config = minimalTestConfig()
        val adapter = ConfigAdapter(config)

        assertEquals(300, adapter.loadAppConfig().replTimeoutSeconds)
    }

    // ──── Missing required keys ────

    @Test
    fun `should throw when llm base-url is missing`() {
        val config = TestConfig(
            mapOf(
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<NoSuchElementException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when llm api-key is missing`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.default-model" to "openai/gpt-4o-mini"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<NoSuchElementException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when llm default-model is missing`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<NoSuchElementException> {
            adapter.loadAppConfig()
        }
    }

    // ──── Validation: invalid values ────

    @Test
    fun `should throw when temperature is negative`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-temperature" to "-0.5"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when temperature exceeds 2_0`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-temperature" to "3.0"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when maxTokens is zero`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-max-tokens" to "0"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when maxTokens exceeds limit`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-max-tokens" to "200000"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when baseUrl is blank`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "   ",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when apiKey is blank`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "",
                "llm.default-model" to "openai/gpt-4o-mini"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when timeoutSeconds is negative`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.timeout-seconds" to "-1"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when replTimeoutSeconds is negative`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "app.repl-timeout-seconds" to "-5"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when replTimeoutSeconds is zero`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "app.repl-timeout-seconds" to "0"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    // ──── Invalid format tests ────

    @Test
    fun `should throw when temperature is not a number`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-temperature" to "hot"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when maxTokens is not a number`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-max-tokens" to "many"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    @Test
    fun `should throw when timeout is not a number`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.timeout-seconds" to "forever"
            )
        )
        val adapter = ConfigAdapter(config)

        assertThrows<IllegalArgumentException> {
            adapter.loadAppConfig()
        }
    }

    // ──── Edge case: boundary values ────

    @Test
    fun `should accept temperature at lower bound`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-temperature" to "0.0"
            )
        )
        val adapter = ConfigAdapter(config)

        val llmConfig = adapter.loadAppConfig().llm
        assertEquals(0.0, llmConfig.defaultTemperature)
    }

    @Test
    fun `should accept temperature at upper bound`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-temperature" to "2.0"
            )
        )
        val adapter = ConfigAdapter(config)

        val llmConfig = adapter.loadAppConfig().llm
        assertEquals(2.0, llmConfig.defaultTemperature)
    }

    @Test
    fun `should accept maxTokens at lower bound`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-max-tokens" to "1"
            )
        )
        val adapter = ConfigAdapter(config)

        val llmConfig = adapter.loadAppConfig().llm
        assertEquals(1, llmConfig.defaultMaxTokens)
    }

    @Test
    fun `should accept maxTokens at upper bound`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "llm.default-max-tokens" to "128000"
            )
        )
        val adapter = ConfigAdapter(config)

        val llmConfig = adapter.loadAppConfig().llm
        assertEquals(128000, llmConfig.defaultMaxTokens)
    }

    @Test
    fun `should accept replTimeoutSeconds at minimum positive value`() {
        val config = TestConfig(
            mapOf(
                "llm.base-url" to "https://api.example.com",
                "llm.api-key" to "test-key",
                "llm.default-model" to "openai/gpt-4o-mini",
                "app.repl-timeout-seconds" to "1"
            )
        )
        val adapter = ConfigAdapter(config)

        assertEquals(1, adapter.loadAppConfig().replTimeoutSeconds)
    }
}
