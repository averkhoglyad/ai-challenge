package io.averkhogliad.ai.challenge.week0.domain.config

import io.averkhogliad.ai.challenge.week0.domain.ModelId

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
 * @property task3 Конфигурация Task3 (промпт-инжиниринг)
 * @property task4 Конфигурация Task4 (бенчмарк температур)
 * @property task5 Конфигурация Task5 (бенчмарк моделей)
 */
data class TaskExecutionConfig(
    val temperature: Double = 0.7,
    val maxTokens: Int = 500,
    val stopSequences: List<String> = emptyList(),
    val modelId: ModelId? = null,
    val task3: Task3Config = Task3Config(),
    val task4: Task4Config = Task4Config(),
    val task5: Task5Config = Task5Config()
) {
    init {
        require(temperature in 0.0..2.0) { "temperature must be in 0.0..2.0, got $temperature" }
        require(maxTokens in 1..128000) { "maxTokens must be in 1..128000, got $maxTokens" }
        require(stopSequences.size <= 4) { "stopSequences cannot exceed 4, got ${stopSequences.size}" }
    }
}
