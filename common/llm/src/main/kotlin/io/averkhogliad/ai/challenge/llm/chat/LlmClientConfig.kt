package io.averkhogliad.ai.challenge.llm.chat

import io.averkhogliad.ai.challenge.llm.config.Config
import kotlin.time.Duration

/**
 * Типизированная конфигурация для [DefaultLlmClient].
 *
 * Все значения уже распарсены из строковых ключей [Config] в composition root.
 *
 * @property baseUrl Базовый URL API (например, "https://api.openai.com")
 * @property apiKey API ключ для аутентификации
 * @property model Идентификатор модели по умолчанию (например, "gpt-4", "minimax/minimax-m3")
 * @property connectTimeout Таймаут подключения
 * @property requestTimeout Таймаут запроса
 * @property rateLimitEnabled Включить/выключить rate limiting
 * @property minInterval Минимальный интервал между запросами
 * @property maxRequestsPerMinute Максимальное количество запросов в минуту
 */
data class LlmClientConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val connectTimeout: Duration,
    val requestTimeout: Duration,
    val rateLimitEnabled: Boolean,
    val minInterval: Duration,
    val maxRequestsPerMinute: Int
) {
    /**
     * Переопределён, чтобы исключить утечку [apiKey] при логировании.
     */
    override fun toString(): String {
        return "LlmClientConfig(baseUrl='$baseUrl', apiKey='***', model='$model', " +
                "connectTimeout=$connectTimeout, requestTimeout=$requestTimeout, " +
                "rateLimitEnabled=$rateLimitEnabled, minInterval=$minInterval, " +
                "maxRequestsPerMinute=$maxRequestsPerMinute)"
    }

    companion object {
        /**
         * Создаёт [LlmClientConfig] из [Config].
         *
         * Требует наличия всех обязательных ключей:
         * - `api.base-url`, `api.key`, `api.model`
         * - `api.connect-timeout`, `api.request-timeout`
         *
         * Опциональные ключи (со значениями по умолчанию):
         * - `api.rate-limit.enabled` (true)
         * - `api.rate-limit.min-interval` (PT0.5S)
         * - `api.rate-limit.max-requests-per-minute` (60)
         *
         * @param config Источник конфигурации
         * @return Типизированная конфигурация для [DefaultLlmClient]
         * @throws NoSuchElementException если отсутствует обязательный ключ
         * @throws IllegalArgumentException если формат Duration некорректен
         */
        fun fromConfig(config: Config): LlmClientConfig = LlmClientConfig(
            baseUrl = config.get("api.base-url"),
            apiKey = config.get("api.key"),
            model = config.get("api.model"),
            connectTimeout = Duration.parse(config.get("api.connect-timeout")),
            requestTimeout = Duration.parse(config.get("api.request-timeout")),
            rateLimitEnabled = config.getOrDefault("api.rate-limit.enabled", "true").toBoolean(),
            minInterval = Duration.parse(config.getOrDefault("api.rate-limit.min-interval", "PT0.5S")),
            maxRequestsPerMinute = config.getOrDefault("api.rate-limit.max-requests-per-minute", "60")
                .toInt().also { require(it > 0) { "maxRequestsPerMinute must be > 0, got $it" } }
        )
    }
}
