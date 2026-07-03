package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Embedding
import java.util.*

/**
 * Порт для генерации векторных представлений (эмбеддингов) текстов.
 *
 * Поддерживает батчевую обработку для оптимизации HTTP-вызовов.
 */
interface EmbeddingGenerator {

    /**
     * Проверяет доступность сервиса эмбеддингов.
     *
     * @return true, если сервис доступен и готов к работе
     */
    suspend fun healthCheck(): Boolean

    /**
     * Генерирует эмбеддинги для батча текстов.
     *
     * @param texts список пар (chunkId, текст чанка)
     * @return список эмбеддингов в том же порядке
     */
    suspend fun generateBatch(texts: List<Pair<UUID, String>>): List<Embedding>
}
