package io.averkhogliad.ai.challenge.indexer.domain.model

data class IndexedChunk(
    val chunk: Chunk,
    val embedding: Embedding,
)
