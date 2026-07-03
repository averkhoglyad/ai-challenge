package io.averkhogliad.ai.challenge.week4.cli.domain.rag.port

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import java.util.*

/**
 * Порт для поиска релевантных чанков по векторному представлению запроса.
 *
 * Определяет контракт между application-слоем (оркестрация RAG) и
 * infrastructure-слоем (реализация векторного поиска).
 *
 * Реализации могут использовать in-memory вычисления, sqlite-vss, FAISS и т.д.
 */
interface VectorSearchPort {

    /**
     * Ищет наиболее релевантные чанки для заданного embedding-запроса.
     *
     * @param queryEmbedding вектор запроса (той же размерности, что и эмбеддинги чанков)
     * @param runId идентификатор индексационного run, в котором выполняется поиск
     * @param topK максимальное количество возвращаемых чанков
     * @param threshold минимальный порог косинусного сходства для включения чанка в результат
     * @return список релевантных чанков, отсортированный по убыванию score, размером не более [topK]
     */
    suspend fun search(
        queryEmbedding: FloatArray,
        runId: UUID,
        topK: Int,
        threshold: Float
    ): List<RelevantChunk>
}
