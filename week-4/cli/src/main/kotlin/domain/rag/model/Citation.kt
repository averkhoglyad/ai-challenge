package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Цитата — фрагмент текста из документа с метаданными.
 *
 * Используется в [RagAnswer] для обеспечения проверяемости
 * каждого утверждения в ответе LLM.
 *
 * @property chunkId идентификатор чанка в индексе
 * @property text текст фрагмента из документа
 * @property source название документа-источника
 * @property relevanceScore показатель релевантности (косинусное сходство)
 * @property section заголовок секции документа (опционально)
 */
data class Citation(
    val chunkId: String,
    val text: String,
    val source: String,
    val relevanceScore: Float,
    val section: String? = null
)
