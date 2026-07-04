package io.averkhogliad.ai.challenge.week4.cli.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexingRun
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Рендерер результатов RAG-команд.
 *
 * Отвечает только за форматирование текста. Не зависит от Mordant напрямую —
 * использует ANSI-коды, следуя паттерну других CLI-рендереров.
 *
 * Весь пользовательский текст — на русском языке.
 */
class RagCommandRenderer(
    private val ragConfig: RagConfig? = null
) {

    // ──── Общие сообщения ────

    /** Показать информационное сообщение */
    fun showInfo(message: String) {
        println(message)
    }

    /** Показать предупреждение */
    fun showWarning(message: String) {
        println("\u001b[33m$message\u001b[0m")
    }

    // ──── Toggle ────

    fun renderToggleSuccess(runId: String, strategy: String, chunkCount: Int) {
        println("\u001b[32m✓\u001b[0m RAG включён. Используется активный индекс: $runId ($strategy, $chunkCount чанков)")
    }

    fun renderToggleWarningNoIndex() {
        println("\u001b[33m⚠\u001b[0m RAG включён, но активный индекс не выбран.")
        println("   Сначала проиндексируйте документы: :index fixed <путь> или :index structural <путь>")
        println("   Затем активируйте индекс: :index-switch <runId>")
        println("   До этого запросы будут использовать обычный LLM без контекста.")
    }

    fun renderToggleOff() {
        println("RAG выключен")
    }

    // ──── Status ────

    fun renderStatusWithIndex(state: RagSessionState, runId: String, strategy: String, chunkCount: Int) {
        println("Состояние RAG:")
        println("  Статус: ${if (state.enabled) "Включён" else "Выключен"}")
        println("  Активный индекс: $runId")
        println("  Стратегия: ${strategy.uppercase()}")
        println("  Всего чанков: $chunkCount")
        println("  Top-K: ${state.topK}")
        println("  Порог поиска (similarity): ${state.similarityThreshold}")
        println()
        println("  Анти-галлюцинации:")
        val thresholdStatus = if (isThresholdModified(state)) "изменён" else "по умолчанию"
        println("    Порог релевантности: ${state.relevanceThreshold} ($thresholdStatus)")
    }

    fun renderStatusNoIndex(state: RagSessionState) {
        println("Состояние RAG:")
        println("  Статус: ${if (state.enabled) "Включён \u001b[33m⚠\u001b[0m" else "Выключен"}")
        println("  Активный индекс: НЕ ВЫБРАН")
        println("  Top-K: ${state.topK}")
        println("  Порог поиска (similarity): ${state.similarityThreshold}")
        println()
        println("  Анти-галлюцинации:")
        val thresholdStatus = if (isThresholdModified(state)) "изменён" else "по умолчанию"
        println("    Порог релевантности: ${state.relevanceThreshold} ($thresholdStatus)")
        if (state.enabled) {
            println()
            println("\u001b[33m⚠\u001b[0m RAG включён, но индекс не выбран. Запросы будут использовать обычный LLM.")
            println("   Используйте :index-switch <runId> для выбора индекса.")
        }
    }

    // ──── List ────

    fun renderList(runs: List<IndexingRun>, activeRunId: UUID?) {
        println("Доступные индексы:")
        println()
        println("ID          | Стратегия   | Чанков | Источник       | Создан")
        println("------------|-------------|--------|----------------|------------------")

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())

        for (run in runs) {
            val id = run.id.toString().take(8)
            val strategy = run.strategy.name.padEnd(11)
            val chunks = run.totalChunks.toString().padEnd(6)
            val source = run.sourcePath.take(14).padEnd(14)
            val created = formatter.format(run.startedAt)
            println("$id    | $strategy | $chunks | $source | $created")
        }

        if (activeRunId != null) {
            println()
            println("Активный индекс: $activeRunId")
        }

        println()
        println("Используйте :index-switch <runId> для переключения активного индекса.")
    }

    fun renderListEmpty() {
        println("Доступные индексы отсутствуют.")
        println()
        println("Сначала проиндексируйте документы:")
        println("  :index fixed <путь>      — фиксированный размер чанков")
        println("  :index structural <путь> — структурная нарезка (по заголовкам/абзацам)")
    }

    // ──── Task 4: Helpers ────

    private fun isThresholdModified(state: RagSessionState): Boolean {
        val default = ragConfig?.relevanceThreshold ?: return false
        return state.relevanceThreshold != default
    }
}
