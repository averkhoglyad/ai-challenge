package io.averkhogliad.ai.challenge.llm.embedding

/**
 * Интерфейс клиента для генерации векторных представлений (эмбеддингов).
 *
 * Отдельный от [io.averkhogliad.ai.challenge.llm.chat.LlmClient] интерфейс
 * с собственным контрактом: разные endpoints, модели и форматы ответов.
 */
interface EmbeddingClient : AutoCloseable {

    /** Модель, используемая для генерации эмбеддингов. */
    val model: String

    /** Размерность генерируемых векторов. */
    val dimensions: Int

    /**
     * Генерирует эмбеддинги для списка текстов.
     *
     * @param request запрос с текстами и опциональным переопределением модели
     * @return ответ с векторами и метаданными
     */
    suspend fun generate(request: EmbeddingRequest): EmbeddingResponse

    /**
     * Генерирует эмбеддинги для нескольких запросов последовательно.
     * Реализация по умолчанию — последовательный вызов [generate].
     */
    suspend fun generateBatch(requests: List<EmbeddingRequest>): List<EmbeddingResponse> {
        return requests.map { generate(it) }
    }

    /**
     * Проверяет доступность сервиса эмбеддингов.
     *
     * @return true, если сервис доступен и готов к работе
     */
    suspend fun healthCheck(): Boolean
}
