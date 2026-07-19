package io.averkhogliad.ai.challenge.llm.embedding.config

/**
 * Конфигурация клиента эмбеддингов.
 *
 * @property provider конфигурация конкретного провайдера
 * @property batchSize размер батча для генерации эмбеддингов
 * @property dimensions ожидаемая размерность векторов (для валидации, null — не проверять)
 */
data class EmbeddingConfig(
    val provider: EmbeddingProviderConfig,
    val batchSize: Int = 16,
    val dimensions: Int? = null,
)
