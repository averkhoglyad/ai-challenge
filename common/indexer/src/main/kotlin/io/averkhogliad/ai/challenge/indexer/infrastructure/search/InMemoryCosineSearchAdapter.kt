package io.averkhogliad.ai.challenge.indexer.infrastructure.search

import io.averkhogliad.ai.challenge.indexer.domain.model.IndexedChunk
import io.averkhogliad.ai.challenge.indexer.domain.port.VectorSearchPort
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

class InMemoryCosineSearchAdapter : VectorSearchPort {
    private val index = ConcurrentHashMap<java.util.UUID, IndexedChunk>()

    override suspend fun addEmbedding(indexedChunk: IndexedChunk) {
        index[indexedChunk.chunk.id] = indexedChunk
    }

    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<IndexedChunk> {
        return searchWithScores(queryEmbedding, topK).map { it.first }
    }

    override suspend fun searchWithScores(queryEmbedding: FloatArray, topK: Int): List<Pair<IndexedChunk, Float>> {
        return index.values
            .map { indexed -> indexed to cosineSimilarity(queryEmbedding, indexed.embedding.vector) }
            .sortedByDescending { it.second }
            .take(topK)
    }

    override suspend fun clear() {
        index.clear()
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimensions must match" }
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }
}
