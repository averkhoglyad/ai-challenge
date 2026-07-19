package io.averkhogliad.ai.challenge.llm.embedding

/**
 * Stub-реализация [EmbeddingClient], генерирующая детерминированные векторы.
 *
 * Используется в тестах и при недоступности реального сервиса эмбеддингов.
 * Векторы вычисляются на основе hashCode текста — одинаковые тексты
 * всегда получают одинаковые векторы.
 *
 * @param model название модели
 * @param dimensions размерность векторов
 */
class StubEmbeddingClient(
    override val model: String = "stub-model",
    override val dimensions: Int = 384,
) : EmbeddingClient {

    override suspend fun generate(request: EmbeddingRequest): EmbeddingResponse {
        val embeddings = request.texts.mapIndexed { index, text ->
            LlmEmbedding(
                text = text,
                vector = generateDeterministicVector(text),
                index = index,
            )
        }
        return EmbeddingResponse(embeddings, model, null)
    }

    override suspend fun healthCheck(): Boolean = true

    override fun close() {}

    private fun generateDeterministicVector(text: String): FloatArray {
        return FloatArray(dimensions) { i ->
            val hash = text.hashCode() * 31 + i
            (hash.toFloat() / Int.MAX_VALUE) * 2f - 1f
        }
    }
}
