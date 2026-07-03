package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config

/**
 * Корневая конфигурация индексатора.
 *
 * @property chunkSize размер чанка в символах
 * @property overlap размер перекрытия чанков в символах
 * @property embedding конфигурация сервиса эмбеддингов
 */
data class IndexerConfig(
    val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    val overlap: Int = DEFAULT_OVERLAP,
    val embedding: EmbeddingConfig
) {
    companion object {
        const val DEFAULT_CHUNK_SIZE = 500
        const val DEFAULT_OVERLAP = 50
    }
}
