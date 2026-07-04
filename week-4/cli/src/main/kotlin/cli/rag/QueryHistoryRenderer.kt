package io.averkhogliad.ai.challenge.week4.cli.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.QueryHistoryEntry
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Рендерер истории RAG-запросов.
 *
 * Отвечает только за форматирование текста. Не зависит от Mordant.
 * Весь пользовательский текст — на русском языке.
 */
class QueryHistoryRenderer {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    /**
     * Показывает таблицу последних N запросов.
     */
    fun renderHistory(entries: List<QueryHistoryEntry>) {
        if (entries.isEmpty()) {
            println("История запросов пуста.")
            println()
            println("Выполните несколько RAG-запросов, чтобы увидеть историю.")
            return
        }

        println("История запросов (последние ${entries.size}):")
        println()
        println("#     | Режим      | Время   | Score  | Токены | Запрос")
        println("------|------------|---------|--------|--------|-----------------------------")

        for ((index, entry) in entries.withIndex()) {
            val num = (index + 1).toString().padEnd(4)
            val mode = modeName(entry.searchContext.stats.mode).padEnd(10)
            val time = "${entry.searchContext.stats.totalMs}ms".padEnd(7)
            val score = "%.2f".format(entry.searchContext.stats.score.filteredAvg).padEnd(6)
            val tokens = entry.searchContext.stats.tokens.total.toString().padEnd(6)
            val query = entry.query.take(28)
            println("$num | $mode | $time | $score | $tokens | $query")
        }

        println()
        println("Используйте :rag history --detail <id> для детальной статистики.")
    }

    /**
     * Показывает детальную статистику одного запроса.
     */
    fun renderHistoryDetail(entry: QueryHistoryEntry) {
        val stats = entry.searchContext.stats
        val sep = "━".repeat(60)

        println(sep)
        println("Запрос #${entry.id}: ${entry.query}")
        println(sep)
        println("Режим: ${modeName(stats.mode)}")
        println("Время: ${stats.totalMs}ms")
        println("Чанки: ${stats.chunks.initial} → ${stats.chunks.filtered} → ${stats.chunks.final}")
        println("Score: avg ${"%.2f".format(stats.score.filteredAvg)} (initial: ${"%.2f".format(stats.score.initialAvg)})")
        println("Токены:")

        stats.tokens.rewrite?.let { println("  - rewrite: $it") }
        stats.tokens.rerank?.let { println("  - rerank: $it") }
        println("  - answer: ${stats.tokens.answer}")
        println("  - всего: ${stats.tokens.total}")
        println()

        val totalDropped = stats.dropped.byThreshold + stats.dropped.byTopK + stats.dropped.byRerank
        if (totalDropped > 0) {
            println("Отброшено $totalDropped чанков:")
            if (stats.dropped.byThreshold > 0) println("  - ${stats.dropped.byThreshold} ниже порога")
            if (stats.dropped.byTopK > 0) println("  - ${stats.dropped.byTopK} вне top-K")
            if (stats.dropped.byRerank > 0) println("  - ${stats.dropped.byRerank} низкий rerank-score")
        }

        entry.searchContext.rewrittenQuery?.let {
            println()
            println("Переписанный запрос: $it")
        }

        println()
        println("Дата: ${formatter.format(entry.timestamp)}")
        println(sep)
    }

    /**
     * Подтверждение очистки истории.
     */
    fun renderHistoryCleared(count: Int) {
        println("\\u001b[32m✓\\u001b[0m История очищена. Удалено записей: $count")
    }

    /**
     * Сообщение об отсутствии записи.
     */
    fun renderHistoryNotFound(id: Long) {
        println("\\u001b[33m⚠\\u001b[0m Запись с ID=$id не найдена.")
    }

    private fun modeName(mode: io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode): String =
        when (mode) {
            io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode.Raw -> "raw"
            io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode.Filtered -> "filtered"
            io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode.Reranked -> "reranked"
            io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode.Rewrite -> "rewrite"
        }
}
