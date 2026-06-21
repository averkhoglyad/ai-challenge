package io.averkhogliad.ai.challenge.week1.domain.strategy

/**
 * Константы-ключи для метаданных стратегий управления контекстом.
 *
 * Заменяют магические строки в коде стратегий, менеджера и executor'ов.
 * Централизованное хранение ключей упрощает рефакторинг и исключает опечатки.
 *
 * Использование:
 * ```kotlin
 * metadata[StrategyMetadataKeys.WINDOW_SIZE] = 10
 * val strategy = metadata[StrategyMetadataKeys.STRATEGY] as? String
 * ```
 */
object StrategyMetadataKeys {

    /** Название активной стратегии (значение [StrategyType.code]) */
    const val STRATEGY = "strategy"

    /** Размер окна для SlidingWindow (Int) */
    const val WINDOW_SIZE = "windowSize"

    /** Размер блока компрессии для SlidingWindow (Int) */
    const val BLOCK_SIZE = "blockSize"

    /** Количество сжатых (суммаризованных) сообщений (Int) */
    const val COMPRESSED_MESSAGE_COUNT = "compressedMessageCount"

    /** Текст накопленной суммаризации (String) */
    const val NEW_ACCUMULATED_SUMMARY = "newAccumulatedSummary"

    /** Имя текущей активной ветки диалога (String) */
    const val CURRENT_BRANCH = "currentBranch"

    /** Общее количество веток диалога (Int) */
    const val TOTAL_BRANCHES = "totalBranches"

    /** Общее количество чекпоинтов (Int) */
    const val TOTAL_CHECKPOINTS = "totalCheckpoints"

    /** Количество сообщений в текущей ветке (Int) */
    const val BRANCH_MESSAGE_COUNT = "branchMessageCount"

    /** Количество извлечённых фактов (Int) */
    const val FACTS_COUNT = "factsCount"

    /** Строковое представление фактов для включения в контекст LLM (String) */
    const val FACTS = "facts"

    /** Список извлечённых фактов в виде объектов (List<StickyFact>) */
    const val EXTRACTED_FACTS = "extractedFacts"

    /** Текущее состояние стратегии ([StrategyState]) */
    const val STRATEGY_STATE = "strategyState"
}
