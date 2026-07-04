package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.QueryHistoryEntry
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagAnswer
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchContext
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.QueryHistoryRepository

/**
 * Результат агрегированной статистики по запросам.
 */
data class AggregatedStats(
    val totalQueries: Int,
    val byMode: Map<SearchMode, ModeStats>,
    val avgTotalTimeMs: Long,
    val avgTokenUsage: Int
)

/**
 * Статистика по одному режиму поиска.
 */
data class ModeStats(
    val count: Int,
    val avgTimeMs: Long,
    val avgScore: Float,
    val avgTokens: Int
)

/**
 * Сервис истории запросов: CRUD-операции и агрегация метрик.
 *
 * Автоматически сохраняет запросы в [QueryHistoryRepository].
 * Предоставляет методы для получения истории и агрегированной статистики.
 */
class QueryHistoryService(
    private val repository: QueryHistoryRepository
) {

    /**
     * Сохраняет запрос в историю.
     * @return ID новой записи
     */
    suspend fun recordQuery(
        query: String,
        answer: RagAnswer,
        searchContext: SearchContext
    ): Long {
        val entry = QueryHistoryEntry(
            id = 0L,
            query = query,
            answer = answer,
            searchContext = searchContext,
            timestamp = searchContext.stats.timestamp
        )
        return repository.save(entry)
    }

    /** Получить последние N запросов */
    suspend fun getLast(limit: Int = 10): List<QueryHistoryEntry> =
        repository.getLast(limit)

    /** Получить детали запроса по ID */
    suspend fun getDetailed(id: Long): QueryHistoryEntry? =
        repository.getById(id)

    /** Очистить всю историю */
    suspend fun clearHistory() {
        repository.deleteAll()
    }

    /** Количество записей в истории */
    suspend fun count(): Int = repository.count()

    /**
     * Агрегирует метрики по последним N запросам.
     */
    suspend fun getAggregatedStats(limit: Int = 50): AggregatedStats {
        val entries = repository.getLast(limit)
        if (entries.isEmpty()) {
            return AggregatedStats(
                totalQueries = 0,
                byMode = emptyMap(),
                avgTotalTimeMs = 0L,
                avgTokenUsage = 0
            )
        }

        val byMode: Map<SearchMode, List<QueryHistoryEntry>> = entries.groupBy { it.searchContext.stats.mode }

        val modeStats = byMode.mapValues { (_, modeEntries) ->
            val count = modeEntries.size
            val avgTimeMs = modeEntries.map { it.searchContext.stats.totalMs }.average().toLong()
            val avgScore = modeEntries.map { it.searchContext.stats.score.filteredAvg }.average().toFloat()
            val avgTokens = modeEntries.map { it.searchContext.stats.tokens.total }.average().toInt()
            ModeStats(count, avgTimeMs, avgScore, avgTokens)
        }

        return AggregatedStats(
            totalQueries = entries.size,
            byMode = modeStats,
            avgTotalTimeMs = entries.map { it.searchContext.stats.totalMs }.average().toLong(),
            avgTokenUsage = entries.map { it.searchContext.stats.tokens.total }.average().toInt()
        )
    }
}
