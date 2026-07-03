package io.averkhogliad.ai.challenge.week4.cli.application.indexer

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.EmbeddingConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.EmbeddingProviderConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.embedder.OllamaEmbedder
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.embedder.OpenAiEmbedder
import io.ktor.client.HttpClient

/**
 * Фабрика для создания [EmbeddingGenerator] на основе конфигурации.
 *
 * Находится в application-слое, так как является оркестрационным
 * (routing) решением, а не инфраструктурным.
 */
class EmbeddingGeneratorFactory(
    private val httpClient: HttpClient
) {
    /**
     * Создаёт экземпляр [EmbeddingGenerator] в зависимости от [config.provider].
     *
     * @param config конфигурация эмбеддингов
     * @return реализация [EmbeddingGenerator]
     * @throws IllegalArgumentException если провайдер не поддерживается
     */
    fun create(config: EmbeddingConfig): EmbeddingGenerator {
        return when (val providerConfig = config.providerConfig) {
            is EmbeddingProviderConfig.Ollama -> OllamaEmbedder(
                client = httpClient,
                config = providerConfig,
                common = config
            )

            is EmbeddingProviderConfig.OpenAi -> OpenAiEmbedder(
                client = httpClient,
                config = providerConfig,
                common = config
            )
        }
    }
}
