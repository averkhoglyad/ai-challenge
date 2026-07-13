package io.averkhogliad.ai.challenge.week4.cli.cli.indexer

import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexComparison
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexStatistics
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexingRun

/**
 * Рендерер результатов команд индексации.
 *
 * Использует простой текстовый вывод с box-drawing символами,
 * без зависимостей от Mordant.
 */
object IndexResultRenderer {

    // Box-drawing chars
    private const val H = "─"
    private const val V = "│"
    private const val TL = "┌"
    private const val TR = "┐"
    private const val BL = "└"
    private const val BR = "┘"
    private const val TM = "┬"
    private const val BM = "┴"
    private const val LM = "├"
    private const val RM = "┤"
    private const val CROSS = "┼"

    /**
     * Выводит таблицу runs.
     */
    fun renderRuns(runs: List<IndexingRun>, renderer: CliRenderer) {
        if (runs.isEmpty()) {
            renderer.renderInfo("No indexing runs found.")
            return
        }

        val idWidth = 36
        val dateWidth = 19
        val strategyWidth = 11
        val sourceWidth = 30
        val chunksWidth = 7
        val statusWidth = 10

        renderer.renderInfo("Indexing Runs (${runs.size} total):")
        println()

        // Header
        println(
            TL + H.repeat(idWidth) + TM + H.repeat(dateWidth) + TM +
                    H.repeat(strategyWidth) + TM + H.repeat(sourceWidth) + TM +
                    H.repeat(chunksWidth) + TM + H.repeat(statusWidth) + TR
        )

        println(
            V + pad("ID", idWidth) + V + pad("Date", dateWidth) + V +
                    pad("Strategy", strategyWidth) + V + pad("Source", sourceWidth) + V +
                    pad("Chunks", chunksWidth) + V + pad("Status", statusWidth) + V
        )

        println(
            LM + H.repeat(idWidth) + CROSS + H.repeat(dateWidth) + CROSS +
                    H.repeat(strategyWidth) + CROSS + H.repeat(sourceWidth) + CROSS +
                    H.repeat(chunksWidth) + CROSS + H.repeat(statusWidth) + RM
        )

        for (run in runs) {
            val fullId = run.id.toString()
            val date = run.startedAt.toString().replace("T", " ").take(19)
            val strategy = run.strategy.name
            val source = run.sourcePath.let {
                if (it.length > sourceWidth) "..." + it.takeLast(sourceWidth - 3) else it
            }
            val status = run.status.name
            val statusDisplay = when (run.status) {
                io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.RunStatus.COMPLETED -> "\u001B[32m$status\u001B[0m"
                io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.RunStatus.FAILED -> "\u001B[31m$status\u001B[0m"
                io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.RunStatus.RUNNING -> "\u001B[33m$status\u001B[0m"
            }

            println(
                V + pad(fullId, idWidth) + V + pad(date, dateWidth) + V +
                        pad(strategy, strategyWidth) + V + pad(source, sourceWidth) + V +
                        pad(run.totalChunks.toString(), chunksWidth) + V + padAnsi(
                    statusDisplay,
                    status.length,
                    statusWidth
                ) + V
            )
        }

        println(
            BL + H.repeat(idWidth) + BM + H.repeat(dateWidth) + BM +
                    H.repeat(strategyWidth) + BM + H.repeat(sourceWidth) + BM +
                    H.repeat(chunksWidth) + BM + H.repeat(statusWidth) + BR
        )
        println()
    }

    /**
     * Выводит статистику одного run.
     */
    fun renderStats(stats: IndexStatistics, renderer: CliRenderer) {
        renderer.renderInfo("Index Statistics:")
        println()
        println("  Run ID:      ${stats.runId}")
        println("  Strategy:    ${stats.strategy}")
        println("  Source:      ${stats.sourcePath}")
        println("  Total chunks: ${stats.totalChunks}")
        println("  Chunk size:   avg=${stats.avgChunkSize}, min=${stats.minChunkSize}, max=${stats.maxChunkSize}")
        println("  Index size:   ${formatBytes(stats.indexSizeBytes)}")
        println()
        if (stats.bySource.isNotEmpty()) {
            println("  Distribution by source:")
            val maxSourceLen = stats.bySource.keys.maxOfOrNull { it.length } ?: 20
            for ((source, count) in stats.bySource) {
                val display = if (source.length > 60) "..." + source.takeLast(57) else source
                val bar = "█".repeat((count * 40 / stats.totalChunks).coerceAtLeast(1))
                println("    ${padRight(display, maxSourceLen.coerceAtMost(60))}  $bar $count")
            }
        }
        println()
    }

    /**
     * Выводит сравнение двух статистик side-by-side.
     */
    fun renderComparison(comparison: IndexComparison, renderer: CliRenderer) {
        val s1 = comparison.run1
        val s2 = comparison.run2
        renderer.renderInfo("Index Comparison:")
        println()

        val labelWidth = 18
        val colWidth = 30
        println("  " + padRight("Metric", labelWidth) + padRight("Run 1", colWidth) + "Run 2")
        println("  " + H.repeat(labelWidth) + " " + H.repeat(colWidth) + " " + H.repeat(colWidth))

        fun row(label: String, v1: String, v2: String) {
            println("  " + padRight(label, labelWidth) + padRight(v1, colWidth) + v2)
        }

        row("Strategy", s1.strategy.name, s2.strategy.name)
        row("Total chunks", s1.totalChunks.toString(), s2.totalChunks.toString())
        row("Avg chunk size", s1.avgChunkSize.toString(), s2.avgChunkSize.toString())
        row("Min chunk size", s1.minChunkSize.toString(), s2.minChunkSize.toString())
        row("Max chunk size", s1.maxChunkSize.toString(), s2.maxChunkSize.toString())
        row("Index size", formatBytes(s1.indexSizeBytes), formatBytes(s2.indexSizeBytes))
        row("Source path", s1.sourcePath.take(28), s2.sourcePath.take(28))
        println()
    }

    // ──── Helpers ────

    private fun pad(text: String, width: Int): String {
        val plain = stripAnsi(text)
        val padLen = (width - plain.length).coerceAtLeast(0)
        return " " + text + " ".repeat(padLen)
    }

    private fun padAnsi(text: String, plainLen: Int, width: Int): String {
        val padLen = (width - plainLen).coerceAtLeast(0)
        return " " + text + " ".repeat(padLen)
    }

    private fun padRight(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        return text + " ".repeat(width - text.length)
    }

    private fun stripAnsi(text: String): String = text.replace("\u001B\\[[;\\d]*m".toRegex(), "")

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        }
    }
}
