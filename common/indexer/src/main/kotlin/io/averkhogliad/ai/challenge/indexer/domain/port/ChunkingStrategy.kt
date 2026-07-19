package io.averkhogliad.ai.challenge.indexer.domain.port

import io.averkhogliad.ai.challenge.indexer.domain.model.Chunk
import io.averkhogliad.ai.challenge.indexer.domain.model.Document

enum class ChunkingStrategyType {
    FIXED_SIZE,
    STRUCTURAL,
}

interface ChunkingStrategy {
    val type: ChunkingStrategyType

    fun chunk(document: Document): List<Chunk>
}
