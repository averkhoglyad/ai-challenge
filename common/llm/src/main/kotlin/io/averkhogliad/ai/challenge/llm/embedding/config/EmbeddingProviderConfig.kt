package io.averkhogliad.ai.challenge.llm.embedding.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Конфигурация провайдера эмбеддингов.
 *
 * Закрытая (sealed) иерархия для исчерпывающей обработки в фабрике.
 */
sealed interface EmbeddingProviderConfig {

    /** Конфигурация для Ollama. */
    data class Ollama(
        val baseUrl: String = "http://localhost:11434",
        val model: String = "nomic-embed-text",
        val dimensions: Int = 768, // nomic-embed-text
        val timeout: Duration = 30.seconds,
    ) : EmbeddingProviderConfig

    /** Конфигурация для OpenAI и OpenAI-совместимых API. */
    data class OpenAi(
        val baseUrl: String = "https://api.openai.com/v1/embeddings",
        val apiKey: String,
        val model: String = "text-embedding-3-small",
        val dimensions: Int = 1536, // text-embedding-3-small
        val timeout: Duration = 30.seconds,
    ) : EmbeddingProviderConfig
}
