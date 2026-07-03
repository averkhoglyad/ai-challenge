package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config

/**
 * Конфигурация провайдера эмбеддингов.
 *
 * Закрытая (sealed) иерархия для обеспечения исчерпывающей обработки
 * в фабрике [io.averkhogliad.ai.challenge.week4.cli.application.indexer.EmbeddingGeneratorFactory].
 */
sealed class EmbeddingProviderConfig {

    /** Конфигурация для нативной Ollama */
    data class Ollama(
        val url: String,
        val model: String
    ) : EmbeddingProviderConfig()

    /** Конфигурация для OpenAI и OpenAI-совместимых API */
    data class OpenAi(
        val url: String,
        val model: String,
        val apiKey: String?
    ) : EmbeddingProviderConfig()
}
