package io.averkhogliad.ai.challenge.week6.infrastructure.indexer.embedder

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.Embedding
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.EmbeddingGenerator
import java.util.*

/**
 * Stub реализация [EmbeddingGenerator] для MVP.
 *
 * Генерирует случайные векторы фиксированной размерности.
 * Используется, когда реальный сервис эмбеддингов недоступен.
 *
 * @param dimensions размерность векторов
 * @param model название модели
 */
class StubEmbeddingGenerator(
    private val dimensions: Int = 384,
    private val model: String = "stub-model",
) : EmbeddingGenerator {

    override suspend fun healthCheck(): Boolean = true

    override suspend fun generateBatch(texts: List<Pair<UUID, String>>): List<Embedding> {
        return texts.map { (chunkId, text) ->
            val vector = FloatArray(dimensions) { i ->
                val hash = text.hashCode() * 31 + i
                (hash.toFloat() / Int.MAX_VALUE)
            }
            Embedding(chunkId = chunkId, vector = vector, model = model)
        }
    }
}
