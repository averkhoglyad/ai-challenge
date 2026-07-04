package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode

/**
 * Отчёт анализа производительности.
 */
data class AnalysisReport(
    val summary: String,
    val modeStats: Map<SearchMode, ModeStats>,
    val recommendations: List<String>
)

/**
 * Сравнение двух режимов поиска.
 */
data class ModeComparison(
    val mode1: SearchMode,
    val mode2: SearchMode,
    val mode1Stats: ModeStats,
    val mode2Stats: ModeStats,
    val delta: MetricsDelta
)

/**
 * Дельта между двумя режимами в процентах.
 * Положительное значение означает увеличение.
 */
data class MetricsDelta(
    val timeDeltaPercent: Float,
    val scoreDeltaPercent: Float,
    val tokenDeltaPercent: Float
)

/**
 * Анализатор метрик: анализ производительности и сравнение режимов.
 *
 * Использует [QueryHistoryService] для получения истории запросов,
 * вычисляет агрегированные метрики и рекомендации.
 */
class MetricsAnalyzer(
    private val historyService: QueryHistoryService
) {

    /**
     * Анализирует производительность по последним N запросам.
     */
    suspend fun analyze(limit: Int = 50): AnalysisReport {
        val stats = historyService.getAggregatedStats(limit)

        if (stats.totalQueries == 0) {
            return AnalysisReport(
                summary = "Нет данных для анализа. Выполните несколько запросов.",
                modeStats = emptyMap(),
                recommendations = emptyList()
            )
        }

        val recommendations = buildRecommendations(stats)
        val summary = buildSummary(stats)

        return AnalysisReport(
            summary = summary,
            modeStats = stats.byMode,
            recommendations = recommendations
        )
    }

    /**
     * Сравнивает два режима поиска по истории запросов.
     */
    suspend fun compareModes(mode1: SearchMode, mode2: SearchMode): ModeComparison {
        val stats = historyService.getAggregatedStats(limit = 100)

        val mode1Stats = stats.byMode[mode1] ?: ModeStats(0, 0L, 0f, 0)
        val mode2Stats = stats.byMode[mode2] ?: ModeStats(0, 0L, 0f, 0)

        val timeDelta = percentDelta(mode1Stats.avgTimeMs, mode2Stats.avgTimeMs)
        val scoreDelta = percentDelta(mode1Stats.avgScore, mode2Stats.avgScore)
        val tokenDelta = percentDelta(mode1Stats.avgTokens.toFloat(), mode2Stats.avgTokens.toFloat())

        return ModeComparison(
            mode1 = mode1,
            mode2 = mode2,
            mode1Stats = mode1Stats,
            mode2Stats = mode2Stats,
            delta = MetricsDelta(timeDelta, scoreDelta, tokenDelta)
        )
    }

    private fun percentDelta(from: Float, to: Float): Float {
        if (from == 0f) return 0f
        return ((to - from) / from) * 100f
    }

    private fun percentDelta(from: Long, to: Long): Float {
        if (from == 0L) return 0f
        return ((to - from).toFloat() / from) * 100f
    }

    private fun buildRecommendations(stats: AggregatedStats): List<String> {
        val recs = mutableListOf<String>()

        val filtered = stats.byMode[SearchMode.Filtered]
        val reranked = stats.byMode[SearchMode.Reranked]

        if (filtered != null && reranked != null) {
            if (reranked.avgTimeMs > filtered.avgTimeMs * 3 && reranked.avgScore > filtered.avgScore * 1.2f) {
                recs.add(
                    "Reranked в ${"%.1f".format(reranked.avgTimeMs.toFloat() / filtered.avgTimeMs)}x медленнее, но даёт +${
                        "%.0f".format(
                            (reranked.avgScore - filtered.avgScore) * 100
                        )
                    }% к score. Используйте для критичных запросов."
                )
            }
            if (filtered.avgScore >= reranked.avgScore * 0.9f && filtered.avgTimeMs < reranked.avgTimeMs) {
                recs.add("Filtered близок по качеству к Reranked, но быстрее. Рекомендуется как основной режим.")
            }
        }

        val raw = stats.byMode[SearchMode.Raw]
        if (raw != null && filtered != null && filtered.avgScore > raw.avgScore * 1.1f) {
            recs.add("Filtered даёт +${"%.0f".format((filtered.avgScore - raw.avgScore) * 100)}% к score относительно Raw при небольшом замедлении.")
        }

        if (recs.isEmpty()) {
            recs.add("Недостаточно данных для рекомендаций. Выполните больше запросов в разных режимах.")
        }

        return recs
    }

    private fun buildSummary(stats: AggregatedStats): String {
        if (stats.totalQueries == 0) return "Нет данных."

        val bestMode = stats.byMode.maxByOrNull { it.value.avgScore }
        val fastestMode = stats.byMode.minByOrNull { it.value.avgTimeMs }

        val parts = mutableListOf<String>()
        parts.add("Всего запросов: ${stats.totalQueries}")

        if (bestMode != null) {
            parts.add("Лучшее качество: ${modeName(bestMode.key)} (score ${"%.2f".format(bestMode.value.avgScore)})")
        }
        if (fastestMode != null) {
            parts.add("Самый быстрый: ${modeName(fastestMode.key)} (${fastestMode.value.avgTimeMs}ms)")
        }

        return parts.joinToString(" | ")
    }

    private fun modeName(mode: SearchMode): String = when (mode) {
        SearchMode.Raw -> "Raw"
        SearchMode.Filtered -> "Filtered"
        SearchMode.Reranked -> "Reranked"
        SearchMode.Rewrite -> "Rewrite"
    }
}
