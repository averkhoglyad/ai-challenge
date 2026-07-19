package io.averkhogliad.ai.challenge.llm.embedding

import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingProviderConfig
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class OllamaEmbeddingClientTest : FreeSpec({

    "OllamaEmbeddingClient" - {

        "model returns config model" {
            val config = EmbeddingProviderConfig.Ollama(model = "custom-model")
            // model is a val derived from config — no HTTP needed
            val client = OllamaEmbeddingClient(config)
            try {
                client.model shouldBe "custom-model"
            } finally {
                client.close()
            }
        }

        "dimensions returns 768 (nomic-embed-text default)" {
            val config = EmbeddingProviderConfig.Ollama()
            val client = OllamaEmbeddingClient(config)
            try {
                client.dimensions shouldBe 768
            } finally {
                client.close()
            }
        }
    }
})
