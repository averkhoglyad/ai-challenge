package io.averkhogliad.ai.challenge.indexer.application

import io.averkhogliad.ai.challenge.indexer.config.ChunkingConfig
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategyType
import io.averkhogliad.ai.challenge.indexer.infrastructure.chunker.FixedSizeChunker
import io.averkhogliad.ai.challenge.indexer.infrastructure.chunker.StructuralChunker
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class ChunkingStrategyFactoryTest : FreeSpec({

    "ChunkingStrategyFactory" - {

        "creates FixedSizeChunker for FIXED_SIZE strategy" {
            val config = ChunkingConfig(strategy = ChunkingStrategyType.FIXED_SIZE)
            val strategy = ChunkingStrategyFactory.create(config)
            strategy.shouldBeInstanceOf<FixedSizeChunker>()
        }

        "creates StructuralChunker for STRUCTURAL strategy" {
            val config = ChunkingConfig(strategy = ChunkingStrategyType.STRUCTURAL)
            val strategy = ChunkingStrategyFactory.create(config)
            strategy.shouldBeInstanceOf<StructuralChunker>()
        }
    }
})
