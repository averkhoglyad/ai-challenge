package io.averkhogliad.ai.challenge.week0.bootstrap

import io.averkhogliad.ai.challenge.llm.config.TestConfig
import io.averkhogliad.ai.challenge.week0.cli.CliApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Тесты для [ApplicationBootstrap] — composition root приложения.
 *
 * Проверяют:
 * - Корректную сборку всех компонентов архитектуры
 * - Обработку ошибок конфигурации
 */
@DisplayName("ApplicationBootstrap")
class ApplicationBootstrapTest {

    /**
     * Минимальная валидная конфигурация с ключами, требуемыми [ConfigAdapter].
     */
    private fun minimalConfig(): TestConfig = TestConfig(
        mapOf(
            "llm.base-url" to "https://api.example.com",
            "llm.api-key" to "test-api-key",
            "llm.default-model" to "test-model",
            "llm.default-temperature" to "0.7",
            "llm.default-max-tokens" to "500",
            "llm.timeout-seconds" to "60",
            "execution.temperature" to "0.7",
            "execution.max-tokens" to "500",
            "app.repl-timeout-seconds" to "300"
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // Application assembly
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Application assembly")
    inner class ApplicationAssembly {

        @Test
        @DisplayName("should create valid CliApplication with all executors")
        fun `creates application with all executors`() {
            val config = minimalConfig()

            val application = ApplicationBootstrap.createApplication(config)

            assertIs<CliApplication>(application)
            // Проверяем, что application создан без исключений
            // (не запускаем REPL, так как это side-effect)
        }

        @Test
        @DisplayName("should create application with default temperature from config")
        fun `uses config temperature`() {
            val config = TestConfig(
                mapOf(
                    "llm.base-url" to "https://api.example.com",
                    "llm.api-key" to "test-api-key",
                    "llm.default-model" to "test-model",
                    "llm.default-temperature" to "1.5",
                    "llm.default-max-tokens" to "500",
                    "llm.timeout-seconds" to "60",
                    "execution.temperature" to "0.7",
                    "execution.max-tokens" to "500",
                    "app.repl-timeout-seconds" to "300"
                )
            )

            // Не должно быть исключения при валидации LlmConfig (1.5 in 0.0..2.0)
            val application = ApplicationBootstrap.createApplication(config)
            assertIs<CliApplication>(application)
        }

        @Test
        @DisplayName("should create application with custom timeout from config")
        fun `uses config timeout`() {
            val config = TestConfig(
                mapOf(
                    "llm.base-url" to "https://api.example.com",
                    "llm.api-key" to "test-api-key",
                    "llm.default-model" to "test-model",
                    "llm.default-temperature" to "0.7",
                    "llm.default-max-tokens" to "500",
                    "llm.timeout-seconds" to "120",
                    "execution.temperature" to "0.7",
                    "execution.max-tokens" to "500",
                    "app.repl-timeout-seconds" to "300"
                )
            )

            val application = ApplicationBootstrap.createApplication(config)
            assertIs<CliApplication>(application)
        }

        @Test
        @DisplayName("should create application with default values when optional keys are missing")
        fun `uses defaults for missing optional keys`() {
            // Только обязательные ключи
            val config = TestConfig(
                mapOf(
                    "llm.base-url" to "https://api.example.com",
                    "llm.api-key" to "test-api-key",
                    "llm.default-model" to "test-model",
                    "llm.timeout-seconds" to "60",
                    // "llm.default-temperature" — default 0.7
                    // "llm.default-max-tokens" — default 500
                    // "execution.temperature" — default 0.7
                    // "execution.max-tokens" — default 500
                    // "app.repl-timeout-seconds" — default 300
                )
            )

            val application = ApplicationBootstrap.createApplication(config)
            assertIs<CliApplication>(application)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Error handling
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {

        @Test
        @DisplayName("should throw when required config key is missing")
        fun `throws on missing required key`() {
            val config = TestConfig(
                mapOf(
                    "llm.base-url" to "https://api.example.com",
                    "llm.api-key" to "test-api-key"
                    // "llm.default-model" — отсутствует
                )
            )

            assertFailsWith<NoSuchElementException> {
                ApplicationBootstrap.createApplication(config)
            }
        }

        @Test
        @DisplayName("should throw when llm.default-model is blank")
        fun `throws on blank model`() {
            val config = TestConfig(
                mapOf(
                    "llm.base-url" to "https://api.example.com",
                    "llm.api-key" to "test-api-key",
                    "llm.default-model" to "   "
                )
            )

            assertFailsWith<IllegalArgumentException> {
                ApplicationBootstrap.createApplication(config)
            }
        }

        @Test
        @DisplayName("should throw when temperature is out of range")
        fun `throws on invalid temperature`() {
            val config = TestConfig(
                mapOf(
                    "llm.base-url" to "https://api.example.com",
                    "llm.api-key" to "test-api-key",
                    "llm.default-model" to "test-model",
                    "llm.default-temperature" to "5.0",
                    "llm.timeout-seconds" to "60"
                )
            )

            assertFailsWith<IllegalArgumentException> {
                ApplicationBootstrap.createApplication(config)
            }
        }
    }
}
