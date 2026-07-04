package io.averkhogliad.ai.challenge.week4.cli.domain.config

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode

/**
 * Иммутабельная конфигурация RAG-системы, загружаемая из application.properties.
 *
 * Содержит значения по умолчанию для [io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig],
 * которые используются при старте приложения и могут быть переопределены
 * пользователем через CLI-команды (:rag mode/threshold/topk).
 *
 * Зависит только от domain-модели [SearchMode].
 *
 * @property defaultMode режим поиска по умолчанию
 * @property defaultTopKInitial количество чанков из векторного индекса по умолчанию
 * @property defaultTopKFinal количество чанков после фильтрации по умолчанию
 * @property defaultThreshold порог косинусного сходства по умолчанию
 */
data class RagConfig(
    val defaultMode: SearchMode = SearchMode.Filtered,
    val defaultTopKInitial: Int = 50,
    val defaultTopKFinal: Int = 5,
    val defaultThreshold: Float = 0.75f
) {
    init {
        require(defaultTopKInitial > 0) { "defaultTopKInitial must be > 0, got $defaultTopKInitial" }
        require(defaultTopKFinal > 0) { "defaultTopKFinal must be > 0, got $defaultTopKFinal" }
        require(defaultTopKFinal <= defaultTopKInitial) {
            "defaultTopKFinal ($defaultTopKFinal) must be <= defaultTopKInitial ($defaultTopKInitial)"
        }
        require(defaultThreshold in 0.0f..1.0f) { "defaultThreshold must be in 0.0..1.0, got $defaultThreshold" }
    }

    /**
     * Создаёт [io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig]
     * из значений по умолчанию этой конфигурации.
     */
    fun toSearchConfig() = io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig(
        mode = defaultMode,
        topKInitial = defaultTopKInitial,
        topKFinal = defaultTopKFinal,
        threshold = defaultThreshold
    )
}
