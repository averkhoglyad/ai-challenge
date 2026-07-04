package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

import java.time.Instant

/**
 * Запись в истории RAG-запросов.
 *
 * Сохраняется в SQLite-таблицу `query_history` через [QueryHistoryRepository].
 *
 * @property id автоинкрементный идентификатор записи
 * @property query оригинальный запрос пользователя
 * @property answer полный ответ RAG-системы (включая sources, fallback-флаги)
 * @property searchContext контекст поиска с метриками
 * @property timestamp время выполнения запроса
 */
data class QueryHistoryEntry(
    val id: Long,
    val query: String,
    val answer: RagAnswer,
    val searchContext: SearchContext,
    val timestamp: Instant
)
