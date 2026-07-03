package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

/**
 * Чанк вместе с его векторным представлением.
 *
 * Используется как единица хранения в репозитории.
 */
data class IndexedChunk(
    val chunk: Chunk,
    val embedding: Embedding
)
