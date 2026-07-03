package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ExtractedText
import java.util.*

/**
 * Порт для стратегии разбиения текста на чанки.
 *
 * Разные стратегии (FixedSize, Structural) реализуют этот интерфейс.
 */
interface ChunkingStrategy {

    /** Тип стратегии */
    val type: ChunkingStrategyType

    /**
     * Разбивает извлечённый текст на чанки.
     *
     * @param extractedText извлечённый из документа текст с метаданными
     * @param runId идентификатор текущего run индексации
     * @return список чанков
     */
    fun chunk(extractedText: ExtractedText, runId: UUID): List<Chunk>
}
