package io.averkhogliad.ai.challenge.week2.domain.telemetry

/**
 * Domain-модель использования токенов для одного вызова LLM.
 *
 * ## Архитектурные решения
 * - **Immutable** — все поля val, неизменяемый объект-значение
 * - **Functional Core** — чистая domain-логика без побочных эффектов
 * - **Валидация в init** — гарантирует целостность данных
 *
 * @property promptTokens количество токенов во входном промпте
 * @property completionTokens количество токенов в сгенерированном ответе
 * @property totalTokens суммарное количество токенов
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
) {
    init {
        require(promptTokens >= 0) { "promptTokens must be non-negative, got: $promptTokens" }
        require(completionTokens >= 0) { "completionTokens must be non-negative, got: $completionTokens" }
        require(totalTokens == promptTokens + completionTokens) {
            "totalTokens ($totalTokens) must equal promptTokens ($promptTokens) + completionTokens ($completionTokens)"
        }
    }

    companion object {
        /**
         * Создаёт [TokenUsage] из метаданных [TaskResult.Success].
         * Ожидает ключи "promptTokens", "completionTokens", "totalTokens" в metadata.
         *
         * @param metadata словарь метаданных из TaskResult
         * @return [TokenUsage] или null, если метаданные не содержат необходимых ключей
         */
        fun fromMetadata(metadata: Map<String, Any>): TokenUsage? {
            val promptTokens = metadata["promptTokens"] as? Int ?: return null
            val completionTokens = metadata["completionTokens"] as? Int ?: return null
            val totalTokens = metadata["totalTokens"] as? Int ?: return null
            return TokenUsage(promptTokens, completionTokens, totalTokens)
        }

        /**
         * Создаёт [TokenUsage] только с информацией о промпт-токенах
         * (до вызова LLM, когда completion ещё неизвестен).
         */
        fun promptOnly(tokenCount: Int): TokenUsage {
            return TokenUsage(promptTokens = tokenCount, completionTokens = 0, totalTokens = tokenCount)
        }

        /**
         * Создаёт [TokenUsage] только с информацией о completion-токенах
         * (когда известен только ответ модели).
         */
        fun completionOnly(tokenCount: Int): TokenUsage {
            return TokenUsage(promptTokens = 0, completionTokens = tokenCount, totalTokens = tokenCount)
        }
    }
}
