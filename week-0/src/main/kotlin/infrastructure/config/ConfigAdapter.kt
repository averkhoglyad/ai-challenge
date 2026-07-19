package io.averkhogliad.ai.challenge.week0.infrastructure.config

import io.averkhogliad.ai.challenge.llm.chat.ModelInfo
import io.averkhogliad.ai.challenge.llm.config.Config
import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.config.*
import io.averkhogliad.ai.challenge.week0.domain.service.ConfigPort

/**
 * Адаптер, реализующий domain-интерфейс [ConfigPort] с помощью infrastructure [Config].
 *
 * Реализует паттерн Adapter из чистой архитектуры (Clean Architecture):
 * - domain-слой определяет интерфейс [ConfigPort] и НЕ зависит от infrastructure
 * - [ConfigAdapter] находится в infrastructure-слое и маппит строковые ключи [Config]
 *   в типизированные domain-конфиги
 *
 * ## Ключи конфигурации
 *
 * | Ключ                         | Обязательный | Domain-поле                  | По умолчанию |
 * |------------------------------|:-----------:|------------------------------|-------------|
 * | `llm.base-url`               | Да          | LlmConfig.baseUrl            | —           |
 * | `llm.api-key`                | Да          | LlmConfig.apiKey             | —           |
 * | `llm.default-model`          | Да          | LlmConfig.defaultModelId     | —           |
 * | `llm.default-temperature`    | Нет         | LlmConfig.defaultTemperature | 0.7         |
 * | `llm.default-max-tokens`     | Нет         | LlmConfig.defaultMaxTokens   | 500         |
 * | `llm.timeout-seconds`        | Нет         | LlmConfig.timeoutSeconds     | 60          |
 * | `execution.temperature`      | Нет         | TaskExecutionConfig.temperature | 0.7      |
 * | `execution.max-tokens`       | Нет         | TaskExecutionConfig.maxTokens   | 500      |
 * | `app.repl-timeout-seconds`   | Нет         | AppConfig.replTimeoutSeconds    | 300      |
 *
 * ## Обработка ошибок
 *
 * Валидация выполняется через init-блоки domain-конфигов.
 * Ошибки конвертации (например, нечисловая строка для temperature)
 * выбрасывают [IllegalArgumentException] с понятным сообщением.
 *
 * @property config инфраструктурный источник конфигурации
 */
class ConfigAdapter(private val config: Config) : ConfigPort {

    override fun loadAppConfig(): AppConfig {
        val llmConfig = loadLlmConfig()
        val executionConfig = loadDefaultExecutionConfig()
        val replTimeout = loadReplTimeout()

        return AppConfig(
            llm = llmConfig,
            defaultExecution = executionConfig,
            replTimeoutSeconds = replTimeout
        )
    }

    // ──── Private helpers ────

    private fun loadLlmConfig(): LlmConfig {
        // Поддержка старых (api.*) и новых (llm.*) ключей конфигурации
        val baseUrl = config.getWithFallback("llm.base-url", "api.base-url")
        val apiKey = config.getWithFallback("llm.api-key", "api.key")
        val defaultModelIdRaw = config.getWithFallback("llm.default-model", "api.model")
        // api.model поддерживает тот же формат, что и models: "id[:name][(costIn,costOut)]"
        // Извлекаем только modelId для API-запросов
        val defaultModelId = ModelId(ModelInfo.parse(defaultModelIdRaw).modelId)
        val defaultTemperature = config.getOrDefault("llm.default-temperature", "0.7")
            .toDoubleOrNull() ?: throw IllegalArgumentException(
            "Invalid llm.default-temperature: '${config.getOrNull("llm.default-temperature")}'"
        )
        val defaultMaxTokens = config.getOrDefault("llm.default-max-tokens", "500")
            .toIntOrNull() ?: throw IllegalArgumentException(
            "Invalid llm.default-max-tokens: '${config.getOrNull("llm.default-max-tokens")}'"
        )
        val timeoutSeconds = config.getOrDefault("llm.timeout-seconds", "60")
            .toLongOrNull() ?: throw IllegalArgumentException(
            "Invalid llm.timeout-seconds: '${config.getOrNull("llm.timeout-seconds")}'"
        )

        return LlmConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            defaultModelId = defaultModelId,
            defaultTemperature = defaultTemperature,
            defaultMaxTokens = defaultMaxTokens,
            timeoutSeconds = timeoutSeconds
        )
    }

    /**
     * Получает значение из конфигурации с поддержкой fallback-ключей.
     *
     * Пробует ключи по порядку и возвращает первое найденное непустое значение.
     * Если ни один ключ не найден, выбрасывает [NoSuchElementException].
     *
     * @param keys ключи конфигурации в порядке приоритета
     * @return значение конфигурации
     * @throws NoSuchElementException если ни один ключ не найден
     */
    private fun Config.getWithFallback(vararg keys: String): String {
        for (key in keys) {
            val value = getOrNull(key)
            if (value != null) {
                return value
            }
        }
        throw NoSuchElementException(
            "None of the configuration keys found: ${keys.toList()}. " +
                    "Please add one of them to your configuration file."
        )
    }

    private fun loadDefaultExecutionConfig(): TaskExecutionConfig {
        val temperature = config.getOrDefault("execution.temperature", "0.7")
            .toDoubleOrNull() ?: throw IllegalArgumentException(
            "Invalid execution.temperature: '${config.getOrNull("execution.temperature")}'"
        )
        val maxTokens = config.getOrDefault("execution.max-tokens", "500")
            .toIntOrNull() ?: throw IllegalArgumentException(
            "Invalid execution.max-tokens: '${config.getOrNull("execution.max-tokens")}'"
        )

        val task4Config = loadTask4Config()
        val task5Config = loadTask5Config()

        return TaskExecutionConfig(
            temperature = temperature,
            maxTokens = maxTokens,
            task4 = task4Config,
            task5 = task5Config
        )
    }

    /**
     * Загружает конфигурацию Task4 из properties.
     *
     * Ключ: `task4.temperatures` — список значений temperature через запятую (напр. "0.0,0.7,1.2").
     * Если ключ отсутствует, возвращается [Task4Config] со значениями по умолчанию.
     */
    private fun loadTask4Config(): Task4Config {
        val tempsStr = config.getOrNull("task4.temperatures") ?: return Task4Config()
        val temperatures = tempsStr.split(",").map { it.trim() }.map { value ->
            value.toDoubleOrNull() ?: throw IllegalArgumentException(
                "Invalid task4.temperatures value: '$value' in '$tempsStr'"
            )
        }
        return Task4Config(temperatures = temperatures)
    }

    /**
     * Загружает конфигурацию Task5 из properties.
     *
     * Ключ: `task5.model-ids` — список modelId через запятую (напр. "gpt-4,gpt-4o-mini").
     * Если ключ отсутствует, возвращается [Task5Config] с пустым списком (используются дефолтные из bootstrap).
     */
    private fun loadTask5Config(): Task5Config {
        val modelsStr = config.getOrNull("task5.model-ids") ?: return Task5Config()
        val modelIds = modelsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { ModelId(it) }
        return Task5Config(modelIds = modelIds)
    }

    private fun loadReplTimeout(): Long {
        return config.getOrDefault("app.repl-timeout-seconds", "300")
            .toLongOrNull() ?: throw IllegalArgumentException(
            "Invalid app.repl-timeout-seconds: '${config.getOrNull("app.repl-timeout-seconds")}'"
        )
    }
}
