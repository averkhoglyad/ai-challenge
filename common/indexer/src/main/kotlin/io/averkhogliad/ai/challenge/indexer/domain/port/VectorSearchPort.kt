package io.averkhogliad.ai.challenge.indexer.domain.port

import io.averkhogliad.ai.challenge.indexer.domain.model.IndexedChunk

interface VectorSearchPort {
    suspend fun addEmbedding(indexedChunk: IndexedChunk)

    suspend fun addEmbeddings(chunks: List<IndexedChunk>) {
        chunks.forEach { addEmbedding(it) }
    }

    suspend fun search(queryEmbedding: FloatArray, topK: Int = 5): List<IndexedChunk>

    suspend fun searchWithScores(queryEmbedding: FloatArray, topK: Int = 5): List<Pair<IndexedChunk, Float>> {
        return search(queryEmbedding, topK).map { it to 0f }
    }

    suspend fun clear()
}
