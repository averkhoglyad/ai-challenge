package io.averkhogliad.ai.challenge.llm.embedding

/**
 * Запрос на генерацию эмбеддингов.
 *
 * @property texts список текстов для векторизации
 * @property model опциональное переопределение модели (null — используется модель клиента по умолчанию)
 */
data class EmbeddingRequest(
    val texts: List<String>,
    val model: String? = null,
)
