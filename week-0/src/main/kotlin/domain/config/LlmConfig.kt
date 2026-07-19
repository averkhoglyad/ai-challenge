package io.averkhogliad.ai.challenge.week0.domain.config

import io.averkhogliad.ai.challenge.week0.domain.ModelId

/**
 * Immutable конфигурация LLM-клиента.
 *
 * Содержит параметры подключения к LLM API и значения по умолчанию
 * для параметров генерации. Не зависит от [Config][io.averkhogliad.ai.challenge.llm.config.Config],
 * [ConfigProvider][io.averkhogliad.ai.challenge.llm.config.ConfigProvider] и
 * [application.properties].
 *
 * Зависит только от domain-модели [ModelId].
 *
 * @property baseUrl Базовый URL API (например, "https://api.openai.com")
 * @property apiKey API ключ для аутентификации
 * @property defaultModelId Модель по умолчанию для всех задач
 * @property defaultTemperature Температура по умолчанию (0.0 - 2.0)
 * @property defaultMaxTokens Максимальное количество токенов по умолчанию (1 - 128000)
 * @property timeoutSeconds Таймаут запроса в секундах (> 0)
 */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val defaultModelId: ModelId,
    val defaultTemperature: Double = 0.7,
    val defaultMaxTokens: Int = 500,
    val timeoutSeconds: Long = 60
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl cannot be blank" }
        require(apiKey.isNotBlank()) { "apiKey cannot be blank" }
        require(defaultTemperature in 0.0..2.0) { "temperature must be in 0.0..2.0, got $defaultTemperature" }
        require(defaultMaxTokens in 1..128000) { "maxTokens must be in 1..128000, got $defaultMaxTokens" }
        require(timeoutSeconds > 0) { "timeoutSeconds must be positive, got $timeoutSeconds" }
    }

    /**
     * Переопределён, чтобы исключить утечку [apiKey] при логировании.
     */
    override fun toString(): String {
        return "LlmConfig(baseUrl='$baseUrl', apiKey='***', defaultModelId=$defaultModelId, " +
                "temperature=$defaultTemperature, maxTokens=$defaultMaxTokens, " +
                "timeoutSeconds=$timeoutSeconds)"
    }
}
