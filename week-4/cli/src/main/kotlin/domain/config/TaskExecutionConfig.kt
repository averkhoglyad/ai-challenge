package io.averkhogliad.ai.challenge.week4.cli.domain.config

import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId

/**
 * Immutable конфигурация выполнения отдельной задачи.
 *
 * Содержит настройки параметров генерации, специфичные для конкретной задачи.
 * Все поля опциональны относительно [LlmConfig]: если значение не задано (null),
 * используется значение по умолчанию из [LlmConfig].
 *
 * Зависит только от domain-модели [ModelId].
 *
 * @property temperature Температура генерации (0.0 - 2.0)
 * @property maxTokens Максимальное количество токенов в ответе (1 - 128000)
 * @property stopSequences Стоп-последовательности (максимум 4)
 * @property modelId ID модели для выполнения (null = использовать defaultModelId из LlmConfig)
 */
data class TaskExecutionConfig(
    val temperature: Double = 0.7,
    val maxTokens: Int = 500,
    val stopSequences: List<String> = emptyList(),
    val modelId: ModelId? = null
) {
    init {
        require(temperature in 0.0..2.0) { "temperature must be in 0.0..2.0, got $temperature" }
        require(maxTokens in 1..128000) { "maxTokens must be in 1..128000, got $maxTokens" }
        require(stopSequences.size <= 4) { "stopSequences cannot exceed 4, got ${stopSequences.size}" }
    }
}
