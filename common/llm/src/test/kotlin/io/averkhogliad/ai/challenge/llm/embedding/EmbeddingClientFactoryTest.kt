package io.averkhogliad.ai.challenge.llm.embedding

import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingConfig
import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingProviderConfig
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EmbeddingClientFactoryTest : FreeSpec({

    "EmbeddingClientFactory" - {

        "creates OllamaEmbeddingClient for Ollama config" {
            val config = EmbeddingConfig(
                provider = EmbeddingProviderConfig.Ollama(model = "test-model"),
            )

            val client = EmbeddingClientFactory.create(config)

            client.shouldBeInstanceOf<OllamaEmbeddingClient>()
            client.model shouldBe "test-model"
        }

        "creates OpenAiEmbeddingClient for OpenAi config" {
            val config = EmbeddingConfig(
                provider = EmbeddingProviderConfig.OpenAi(apiKey = "key", model = "test-model"),
            )

            val client = EmbeddingClientFactory.create(config)

            client.shouldBeInstanceOf<OpenAiEmbeddingClient>()
            client.model shouldBe "test-model"
        }
    }
})
