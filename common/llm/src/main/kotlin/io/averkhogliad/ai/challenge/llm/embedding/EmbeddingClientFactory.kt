package io.averkhogliad.ai.challenge.llm.embedding

import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingConfig
import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingProviderConfig

/**
 * Фабрика для создания [EmbeddingClient] по конфигурации.
 */
object EmbeddingClientFactory {

    /**
     * Создаёт [EmbeddingClient] на основе [config].
     *
     * @param config конфигурация провайдера и параметры клиента
     * @return готовый к использованию клиент
     */
    fun create(config: EmbeddingConfig): EmbeddingClient = when (config.provider) {
        is EmbeddingProviderConfig.Ollama -> OllamaEmbeddingClient(config.provider)
        is EmbeddingProviderConfig.OpenAi -> OpenAiEmbeddingClient(config.provider)
    }
}
