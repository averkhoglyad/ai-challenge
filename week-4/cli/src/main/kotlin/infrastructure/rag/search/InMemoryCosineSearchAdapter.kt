package io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.search

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexedChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.VectorSearchPort
import java.util.*

/**
 * In-memory адаптер [VectorSearchPort], использующий косинусное сходство.
 *
 * Загружает все чанки (с эмбеддингами) для заданного [runId] из [IndexRepository],
 * вычисляет косинусное сходство с query-вектором, фильтрует по [threshold],
 * сортирует по убыванию score и возвращает top-K.
 *
 * ## Производительность
 * Для MVP достаточно in-memory подхода. При большом количестве чанков (>10 000)
 * следует рассмотреть sqlite-vss или FAISS.
 *
 * @param indexRepository репозиторий для доступа к проиндексированным чанкам
 */
class InMemoryCosineSearchAdapter(
    private val indexRepository: IndexRepository
) : VectorSearchPort {

    override suspend fun search(
        queryEmbedding: FloatArray,
        runId: UUID,
        topK: Int,
        threshold: Float
    ): List<RelevantChunk> {
        val chunks: List<IndexedChunk> = indexRepository.getChunksByRunId(runId)

        return chunks
            .map { indexed ->
                RelevantChunk(
                    chunk = indexed.chunk,
                    score = cosineSimilarity(queryEmbedding, indexed.embedding.vector)
                )
            }
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(topK)
    }
}
