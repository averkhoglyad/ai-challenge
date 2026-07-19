package io.averkhogliad.ai.challenge.llm.embedding

import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingProviderConfig
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class OpenAiEmbeddingClientTest : FreeSpec({

    "OpenAiEmbeddingClient" - {

        "model returns config model" {
            val config = EmbeddingProviderConfig.OpenAi(apiKey = "test-key", model = "custom-model")
            val client = OpenAiEmbeddingClient(config)
            try {
                client.model shouldBe "custom-model"
            } finally {
                client.close()
            }
        }

        "dimensions returns 1536 (text-embedding-3-small default)" {
            val config = EmbeddingProviderConfig.OpenAi(apiKey = "test-key")
            val client = OpenAiEmbeddingClient(config)
            try {
                client.dimensions shouldBe 1536
            } finally {
                client.close()
            }
        }
    }
})
