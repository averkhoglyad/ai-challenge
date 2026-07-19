package io.averkhogliad.ai.challenge.week6.infrastructure.rag

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexedChunk
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * In-memory векторное хранилище с поиском по косинусному сходству.
 *
 * Хранит чанки в HashMap<UUID, IndexedChunk>.
 * Поддерживает добавление и поиск по эмбеддингу запроса.
 */
class InMemoryVectorSearch {

    // TODO: Day N — persist embeddings to SQLite for incremental indexing across restarts
    private val store = ConcurrentHashMap<UUID, IndexedChunk>()

    fun addEmbedding(chunk: IndexedChunk) {
        store[chunk.chunk.id] = chunk
    }

    fun search(queryEmbedding: FloatArray, topK: Int): List<Pair<IndexedChunk, Float>> {
        val results = store.values.map { indexedChunk ->
            val similarity = cosineSimilarity(queryEmbedding, indexedChunk.embedding.vector)
            indexedChunk to similarity
        }

        return results
            .sortedByDescending { it.second }
            .take(topK)
    }

    fun clear() {
        store.clear()
    }

    fun size(): Int = store.size

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }
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
