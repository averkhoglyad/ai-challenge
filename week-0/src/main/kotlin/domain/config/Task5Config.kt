package io.averkhogliad.ai.challenge.week0.domain.config

import io.averkhogliad.ai.challenge.week0.domain.ModelId

/**
 * Immutable конфигурация Task5 (сравнение производительности моделей).
 *
 * Содержит параметры, специфичные для Task5:
 * список modelId для бенчмарка.
 *
 * Если список [modelIds] пуст, [Task5Executor] использует modelIds,
 * переданные в конструктор (из [ApplicationBootstrap]).
 *
 * @property modelIds Список ID моделей для бенчмарка
 * @property isEmpty Удобный доступ: true если modelIds пуст
 * @property isNotEmpty Удобный доступ: true если modelIds не пуст
 */
data class Task5Config(
    val modelIds: List<ModelId> = emptyList()
) {
    val isEmpty: Boolean get() = modelIds.isEmpty()
    val isNotEmpty: Boolean get() = modelIds.isNotEmpty()
}
