package io.averkhogliad.ai.challenge.llm.embedding

/**
 * Ответ с эмбеддингами от сервиса.
 *
 * @property embeddings список векторов в порядке исходных текстов
 * @property model модель, сгенерировавшая эмбеддинги
 * @property usage статистика использования токенов (опционально)
 */
data class EmbeddingResponse(
    val embeddings: List<LlmEmbedding>,
    val model: String,
    val usage: TokenUsage? = null,
)
