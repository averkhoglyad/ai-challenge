package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config

/**
 * Конфигурация сервиса эмбеддингов.
 *
 * @property provider идентификатор провайдера: "ollama" или "openai"
 * @property batchSize размер батча для генерации эмбеддингов
 * @property timeoutSeconds таймаут HTTP-запроса в секундах
 * @property retryAttempts количество повторных попыток при ошибке
 * @property retryInitialDelayMs начальная задержка перед повтором (мс)
 * @property providerConfig конфигурация конкретного провайдера
 */
data class EmbeddingConfig(
    val batchSize: Int,
    val timeoutSeconds: Int,
    val retryAttempts: Int = DEFAULT_RETRY_ATTEMPTS,
    val retryInitialDelayMs: Long = DEFAULT_RETRY_INITIAL_DELAY_MS,
    val providerConfig: EmbeddingProviderConfig
) {
    /** Идентификатор провайдера, производный от [providerConfig] */
    val provider: String
        get() = when (providerConfig) {
            is EmbeddingProviderConfig.Ollama -> "ollama"
            is EmbeddingProviderConfig.OpenAi -> "openai"
        }

    companion object {
        const val DEFAULT_RETRY_ATTEMPTS = 3
        const val DEFAULT_RETRY_INITIAL_DELAY_MS = 1000L
    }
}
