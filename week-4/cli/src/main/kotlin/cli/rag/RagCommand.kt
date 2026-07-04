package io.averkhogliad.ai.challenge.week4.cli.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode

/**
 * Типизированное представление RAG-команд.
 *
 * Sealed interface обеспечивает исчерпывающую обработку в when-выражениях.
 */
sealed interface RagCommand {
    // ──── Базовые команды (Task 2) ────
    /** Переключить RAG on/off (toggle) */
    data object Toggle : RagCommand

    /** Показать текущее состояние RAG */
    data object Status : RagCommand

    /** Показать список доступных индексов */
    data object List : RagCommand

    // ──── Управление режимами (Task 3) ────
    /** Установить режим поиска: :rag mode <raw|filtered|reranked|rewrite> */
    data class SetMode(val mode: SearchMode) : RagCommand

    /** Установить порог фильтрации: :rag threshold <value> */
    data class SetThreshold(val threshold: Float) : RagCommand

    /** Установить top-K: :rag topk <initial> <final> */
    data class SetTopK(val initial: Int, val final: Int) : RagCommand

    /** Показать текущую конфигурацию: :rag config */
    data object Config : RagCommand

    // ──── История и аналитика (Task 3) ────
    /** Показать последние N запросов: :rag history [N] */
    data class History(val limit: Int) : RagCommand

    /** Показать детали запроса: :rag history --detail <id> */
    data class HistoryDetail(val id: Long) : RagCommand

    /** Анализ производительности: :rag analyze */
    data object Analyze : RagCommand

    /** Сравнение двух режимов: :rag analyze --compare <mode1> <mode2> */
    data class Compare(val mode1: SearchMode, val mode2: SearchMode) : RagCommand

    /** Очистить историю: :rag history --clear */
    data object HistoryClear : RagCommand
}
