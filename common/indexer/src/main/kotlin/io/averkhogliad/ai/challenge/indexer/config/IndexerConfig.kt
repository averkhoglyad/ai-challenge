package io.averkhogliad.ai.challenge.indexer.config

import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategyType

data class ChunkingConfig(
    val strategy: ChunkingStrategyType = ChunkingStrategyType.FIXED_SIZE,
    val chunkSize: Int = 500,
    val overlap: Int = 50,
)

data class SearchConfig(
    val topK: Int = 5,
)

data class IndexerConfig(
    val chunking: ChunkingConfig = ChunkingConfig(),
    val search: SearchConfig = SearchConfig(),
)
