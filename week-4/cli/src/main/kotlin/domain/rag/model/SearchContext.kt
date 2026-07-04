package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Контекст поиска — агрегат, собираемый [SearchPipeline] по результатам
 * полного цикла поиска (rewrite → embed → search → rerank).
 *
 * @property query оригинальный запрос пользователя
 * @property rewrittenQuery переписанный запрос (null если rewrite не выполнялся)
 * @property rawResults все чанки из векторного поиска (topK_initial)
 * @property filteredResults чанки после фильтрации/реранкинга (topK_final)
 * @property droppedChunks отброшенные чанки с причинами отсева
 * @property stats метрики выполнения (5 ключевых метрик)
 */
data class SearchContext(
    val query: String,
    val rewrittenQuery: String?,
    val rawResults: List<RelevantChunk>,
    val filteredResults: List<RelevantChunk>,
    val droppedChunks: List<DroppedChunk>,
    val stats: QueryExecutionStats
)
