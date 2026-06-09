package io.averkhogliad.ai.challenge.week0.domain.config

import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.Prompt

/**
 * Immutable конфигурация бенчмарка (сравнение моделей).
 *
 * Используется в Task5 для сравнения производительности моделей на одном промпте.
 * Содержит список моделей для тестирования, значения temperature и общий промпт.
 *
 * Зависит только от domain-моделей [ModelId] и [Prompt].
 *
 * @property modelIds Список ID моделей для бенчмарка (не пустой)
 * @property temperatures Значения temperature для тестирования (не пустой, все в 0.0..2.0)
 * @property maxTokens Максимальное количество токенов (1 - 128000)
 * @property prompt Промпт для бенчмарка
 */
data class BenchmarkConfig(
    val modelIds: List<ModelId>,
    val temperatures: List<Double> = listOf(0.0, 0.7, 1.2),
    val maxTokens: Int = 500,
    val prompt: Prompt
) {
    init {
        require(modelIds.isNotEmpty()) { "modelIds cannot be empty" }
        require(temperatures.isNotEmpty()) { "temperatures cannot be empty" }
        require(temperatures.all { it in 0.0..2.0 }) {
            val invalid = temperatures.filter { it !in 0.0..2.0 }
            "all temperatures must be in 0.0..2.0, invalid: $invalid"
        }
        require(maxTokens in 1..128000) { "maxTokens must be in 1..128000, got $maxTokens" }
    }
}
