package io.averkhogliad.ai.challenge.week4.cli.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.QueryExecutionStats
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode

/**
 * Рендерер RAG-ответов: конфигурационный блок, предупреждения, секция источников.
 *
 * Не зависит от Mordant напрямую — использует ANSI-коды и println.
 * Весь пользовательский текст — на русском языке.
 */
object RagAnswerRenderer {

    /**
     * Показывает конфигурационный блок перед LLM-запросом.
     */
    fun renderConfigBlock(
        config: TaskExecutionConfig,
        ragState: RagSessionState,
        activeRunId: String?,
        strategy: String?,
        chunkCount: Int?
    ) {
        val modelName = config.modelId?.value ?: "default"
        println("[LLM] Модель: $modelName, Температура: ${config.temperature}")

        if (ragState.enabled) {
            if (activeRunId != null && strategy != null && chunkCount != null) {
                println("[RAG] Включён, Индекс: $activeRunId (${strategy.uppercase()}, $chunkCount чанков), Top-K: ${ragState.topK}, Порог: ${ragState.similarityThreshold}")
            } else {
                println("[RAG] Включён, Индекс: НЕ ВЫБРАН \u001b[33m⚠\u001b[0m")
            }
        } else {
            println("[RAG] Выключен")
        }
        println()
    }

    /**
     * Предупреждение: RAG включён, но индекс не выбран.
     */
    fun renderFallbackWarningNoIndex() {
        println("\u001b[33m⚠\u001b[0m RAG включён, но активный индекс не настроен.")
        println("   Выполняется обычный запрос к LLM без контекста.")
        println()
        println("   Для включения RAG:")
        println("   1. Проиндексируйте документы: :index fixed <путь> или :index structural <путь>")
        println("   2. Активируйте индекс: :index-switch <runId>")
        println("   3. Используйте :rag list для просмотра доступных индексов")
        println()
    }

    /**
     * Предупреждение: индекс есть, но поиск не нашёл чанков выше threshold.
     */
    fun renderFallbackWarningEmptySearch(threshold: Float) {
        println("\u001b[33m⚠\u001b[0m Не найдено релевантных чанков (порог: $threshold).")
        println("   Выполняется обычный запрос к LLM без контекста.")
        println()
        println("   Попробуйте:")
        println("   - Переформулировать вопрос")
        println("   - Переключиться на другой индекс: :index-switch <runId>")
        println()
    }

    /**
     * Предупреждение: ошибка генерации embedding для запроса.
     */
    fun renderFallbackWarningEmbeddingError() {
        println("\u001b[33m⚠\u001b[0m Не удалось сгенерировать embedding для запроса.")
        println("   Выполняется обычный запрос к LLM без контекста.")
        println()
        println("   Проверьте доступность embedding-модели и повторите позже.")
        println()
    }

    /**
     * Предупреждение: ошибка векторного поиска.
     */
    fun renderFallbackWarningSearchError() {
        println("\u001b[33m⚠\u001b[0m Ошибка при выполнении векторного поиска.")
        println("   Выполняется обычный запрос к LLM без контекста.")
        println()
        println("   Попробуйте переиндексировать документы или обратитесь к логам для диагностики.")
        println()
    }

    /**
     * Краткая статистика после RAG-запроса.
     */
    fun renderStatsSummary(stats: QueryExecutionStats) {
        println()
        println("📊 Статистика запроса:")
        println("  Режим: ${modeDisplayName(stats.mode)}")
        println("  Время: ${stats.totalMs}ms")
        println("  Чанки: ${stats.chunks.initial} → ${stats.chunks.filtered} → ${stats.chunks.final}")
        println("  Средний score: ${"%.2f".format(stats.score.filteredAvg)}")
        println("  Токены: ${stats.tokens.total}")
    }

    private fun modeDisplayName(mode: SearchMode): String = when (mode) {
        SearchMode.Raw -> "raw"
        SearchMode.Filtered -> "filtered"
        SearchMode.Reranked -> "reranked"
        SearchMode.Rewrite -> "rewrite"
    }

    /**
     * Секция «Использованные источники» после ответа LLM.
     */
    fun renderSources(sources: List<RelevantChunk>) {
        if (sources.isEmpty()) return

        val separator = "━".repeat(55)
        println(separator)
        println("Использованные источники (${sources.size} чанков):")
        println()

        for ((index, relevant) in sources.withIndex()) {
            val num = index + 1
            val source = relevant.chunk.source
            val score = relevant.score
            val preview = relevant.chunk.text.take(200).replace("\n", " ")
            println("$num. $source (релевантность: ${"%.2f".format(score)})")
            println("   $preview")
            if (index < sources.size - 1) println()
        }
        println(separator)
    }
}
