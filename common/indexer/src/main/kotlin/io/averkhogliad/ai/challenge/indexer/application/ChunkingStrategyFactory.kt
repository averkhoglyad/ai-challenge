package io.averkhogliad.ai.challenge.indexer.application

import io.averkhogliad.ai.challenge.indexer.config.ChunkingConfig
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategyType
import io.averkhogliad.ai.challenge.indexer.infrastructure.chunker.FixedSizeChunker
import io.averkhogliad.ai.challenge.indexer.infrastructure.chunker.StructuralChunker

object ChunkingStrategyFactory {
    fun create(config: ChunkingConfig): ChunkingStrategy = when (config.strategy) {
        ChunkingStrategyType.FIXED_SIZE -> FixedSizeChunker(config.chunkSize, config.overlap)
        ChunkingStrategyType.STRUCTURAL -> StructuralChunker(maxSectionSize = config.chunkSize)
    }
}
