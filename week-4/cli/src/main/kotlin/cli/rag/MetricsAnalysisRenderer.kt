package io.averkhogliad.ai.challenge.week4.cli.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.AnalysisReport
import io.averkhogliad.ai.challenge.week4.cli.application.rag.ModeComparison
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode

/**
 * Рендерер аналитики и сравнения режимов RAG.
 *
 * Отвечает только за форматирование текста. Не зависит от Mordant.
 * Весь пользовательский текст — на русском языке.
 */
class MetricsAnalysisRenderer {

    /**
     * Показывает отчёт анализа производительности.
     */
    fun renderAnalysis(report: AnalysisReport) {
        val sep = "━".repeat(60)

        println(sep)
        println("Анализ производительности")
        println(sep)
        println(report.summary)
        println()

        if (report.modeStats.isNotEmpty()) {
            println("По режимам:")
            for ((mode, stats) in report.modeStats) {
                println(
                    "  ${modeName(mode)} (${stats.count} запросов): avg ${stats.avgTimeMs}ms, score ${
                        "%.2f".format(
                            stats.avgScore
                        )
                    }, ${stats.avgTokens} токенов"
                )
            }
            println()
        }

        if (report.recommendations.isNotEmpty()) {
            println("💡 Рекомендации:")
            for (rec in report.recommendations) {
                println("   - $rec")
            }
        }

        println(sep)
    }

    /**
     * Показывает сравнение двух режимов.
     */
    fun renderComparison(comparison: ModeComparison) {
        val sep = "━".repeat(60)

        println(sep)
        println("Сравнение режимов: ${modeName(comparison.mode1)} vs ${modeName(comparison.mode2)}")
        println(sep)
        println("Метрика           | ${modeName(comparison.mode1).padEnd(8)} | ${modeName(comparison.mode2).padEnd(8)} | Дельта")
        println("------------------|----------|----------|--------")

        val time1 = "${comparison.mode1Stats.avgTimeMs}ms".padEnd(8)
        val time2 = "${comparison.mode2Stats.avgTimeMs}ms".padEnd(8)
        println("Среднее время     | $time1 | $time2 | ${deltaStr(comparison.delta.timeDeltaPercent)}")

        val score1 = "%.2f".format(comparison.mode1Stats.avgScore).padEnd(8)
        val score2 = "%.2f".format(comparison.mode2Stats.avgScore).padEnd(8)
        println("Средний score     | $score1 | $score2 | ${deltaStr(comparison.delta.scoreDeltaPercent)}")

        val tok1 = comparison.mode1Stats.avgTokens.toString().padEnd(8)
        val tok2 = comparison.mode2Stats.avgTokens.toString().padEnd(8)
        println("Средние токены    | $tok1 | $tok2 | ${deltaStr(comparison.delta.tokenDeltaPercent)}")

        println()
        println("💡 ${buildComparisonConclusion(comparison)}")
        println(sep)
    }

    /**
     * Показывает текущую конфигурацию.
     */
    fun renderConfig(config: SearchConfig) {
        println("Текущая конфигурация RAG:")
        println("  Режим: ${modeName(config.mode)}")
        println("  Top-K initial: ${config.topKInitial}")
        println("  Top-K final: ${config.topKFinal}")
        println("  Порог: ${config.threshold}")
    }

    /**
     * Подтверждение смены режима.
     */
    fun renderModeChanged(oldMode: SearchMode, newMode: SearchMode) {
        println("📝 Режим изменён: ${modeName(oldMode)} → ${modeName(newMode)}")
    }

    /**
     * Подтверждение смены порога.
     */
    fun renderThresholdChanged(threshold: Float) {
        println("📝 Порог изменён: ${threshold}")
    }

    /**
     * Подтверждение смены top-K.
     */
    fun renderTopKChanged(initial: Int, final: Int) {
        println("📝 Top-K изменён: initial=$initial, final=$final")
    }

    /**
     * Ошибка: невалидный режим.
     */
    fun renderInvalidMode(input: String) {
        println("\\u001b[31m✗\\u001b[0m Неизвестный режим: \"$input\"")
        println("   Доступные режимы: raw, filtered, reranked, rewrite")
    }

    private fun modeName(mode: SearchMode): String = when (mode) {
        SearchMode.Raw -> "raw"
        SearchMode.Filtered -> "filtered"
        SearchMode.Reranked -> "reranked"
        SearchMode.Rewrite -> "rewrite"
    }

    private fun deltaStr(delta: Float): String {
        if (delta == 0f) return "—"
        val sign = if (delta > 0) "+" else ""
        return "$sign${"%.0f".format(delta)}%"
    }

    private fun buildComparisonConclusion(comparison: ModeComparison): String {
        val m1 = modeName(comparison.mode1)
        val m2 = modeName(comparison.mode2)

        if (comparison.mode1Stats.count == 0 && comparison.mode2Stats.count == 0) {
            return "Недостаточно данных для сравнения. Выполните запросы в обоих режимах."
        }
        if (comparison.mode1Stats.count == 0) return "Нет данных для режима $m1."
        if (comparison.mode2Stats.count == 0) return "Нет данных для режима $m2."

        val scoreDelta = comparison.delta.scoreDeltaPercent
        val timeDelta = comparison.delta.timeDeltaPercent

        return when {
            scoreDelta > 10 && timeDelta < 50 ->
                "$m2 улучшает score на ${"%.0f".format(scoreDelta)}% при умеренном замедлении. Рекомендуется для критичных запросов."

            scoreDelta > 5 && timeDelta > 100 ->
                "$m2 даёт +${"%.0f".format(scoreDelta)}% к score, но в ${"%.1f".format(timeDelta / 100 + 1)}x медленнее. Используйте выборочно."

            scoreDelta < 5 ->
                "$m1 и $m2 близки по качеству. Выбирайте по скорости."

            else ->
                "Разница в score: ${"%.0f".format(scoreDelta)}%, времени: ${"%.0f".format(timeDelta)}%."
        }
    }
}
