package io.averkhogliad.ai.challenge.week4.cli.infrastructure.config

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.ModelInfo
import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week4.cli.domain.config.AppConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.config.LlmConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ConfigPort

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
        val ragConfig = loadRagConfig()
        val replTimeout = loadReplTimeout()

        return AppConfig(
            llm = llmConfig,
            defaultExecution = executionConfig,
            rag = ragConfig,
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
        val tokenEstimateCharsPerToken = config.getOrDefault("llm.token-estimate-chars-per-token", "4")
            .toIntOrNull() ?: throw IllegalArgumentException(
            "Invalid llm.token-estimate-chars-per-token: '${config.getOrNull("llm.token-estimate-chars-per-token")}'"
        )

        return LlmConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            defaultModelId = defaultModelId,
            defaultTemperature = defaultTemperature,
            defaultMaxTokens = defaultMaxTokens,
            timeoutSeconds = timeoutSeconds,
            tokenEstimateCharsPerToken = tokenEstimateCharsPerToken
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

        return TaskExecutionConfig(
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    private fun loadRagConfig(): RagConfig {
        val mode = parseSearchMode(config.getOrDefault("rag.search.mode", "filtered"))
        val topKInitial = config.getOrDefault("rag.search.top-k-initial", "50")
            .toIntOrNull() ?: throw IllegalArgumentException(
            "Invalid rag.search.top-k-initial: '${config.getOrNull("rag.search.top-k-initial")}'"
        )
        val topKFinal = config.getOrDefault("rag.search.top-k-final", "5")
            .toIntOrNull() ?: throw IllegalArgumentException(
            "Invalid rag.search.top-k-final: '${config.getOrNull("rag.search.top-k-final")}'"
        )
        val threshold = config.getOrDefault("rag.search.threshold", "0.75")
            .toFloatOrNull() ?: throw IllegalArgumentException(
            "Invalid rag.search.threshold: '${config.getOrNull("rag.search.threshold")}'"
        )

        // ──── Task 4: Anti-hallucination ────
        val relevanceThreshold = config.getOrDefault("rag.anti-hallucination.relevance-threshold", "0.70")
            .toFloatOrNull() ?: throw IllegalArgumentException(
            "Invalid rag.anti-hallucination.relevance-threshold"
        )
        val maxCitations = config.getOrDefault("rag.anti-hallucination.max-citations", "5")
            .toIntOrNull() ?: throw IllegalArgumentException(
            "Invalid rag.anti-hallucination.max-citations"
        )
        val minCitations = config.getOrDefault("rag.anti-hallucination.min-citations", "1")
            .toIntOrNull() ?: throw IllegalArgumentException(
            "Invalid rag.anti-hallucination.min-citations"
        )

        return RagConfig(
            defaultMode = mode,
            defaultTopKInitial = topKInitial,
            defaultTopKFinal = topKFinal,
            defaultThreshold = threshold,
            relevanceThreshold = relevanceThreshold,
            maxCitationsPerAnswer = maxCitations,
            minCitationsRequired = minCitations
        )
    }

    private fun parseSearchMode(raw: String): SearchMode = when (raw.lowercase()) {
        "raw" -> SearchMode.Raw
        "filtered" -> SearchMode.Filtered
        "reranked" -> SearchMode.Reranked
        "rewrite" -> SearchMode.Rewrite
        else -> throw IllegalArgumentException(
            "Invalid rag.search.mode: '$raw'. Expected one of: raw, filtered, reranked, rewrite"
        )
    }

    private fun loadReplTimeout(): Long {
        return config.getOrDefault("app.repl-timeout-seconds", "300")
            .toLongOrNull() ?: throw IllegalArgumentException(
            "Invalid app.repl-timeout-seconds: '${config.getOrNull("app.repl-timeout-seconds")}'"
        )
    }
}
