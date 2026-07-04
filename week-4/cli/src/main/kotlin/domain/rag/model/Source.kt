package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Источник — ссылка на документ и чанк, использованные в ответе.
 *
 * В отличие от [Citation], содержит только метаданные без текста фрагмента.
 * Используется для краткого списка источников в ответе.
 *
 * @property chunkId идентификатор чанка в индексе
 * @property documentName название документа-источника
 * @property relevanceScore показатель релевантности
 */
data class Source(
    val chunkId: String,
    val documentName: String,
    val relevanceScore: Float
)
