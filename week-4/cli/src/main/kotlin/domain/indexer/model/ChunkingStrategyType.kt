package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

/**
 * Стратегия разбиения текста на чанки.
 */
enum class ChunkingStrategyType {
    /** Разбиение окнами фиксированного размера с перекрытием */
    FIXED_SIZE,

    /** Разбиение по структуре документа (заголовки, абзацы) */
    STRUCTURAL
}
