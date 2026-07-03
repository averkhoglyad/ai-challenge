package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

/**
 * Результат сравнения двух индексов.
 *
 * Используется командой `:index-compare`.
 */
data class IndexComparison(
    val run1: IndexStatistics,
    val run2: IndexStatistics
)
