package io.averkhogliad.ai.challenge.llm.embedding

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class StubEmbeddingClientTest : FreeSpec({

    "StubEmbeddingClient" - {

        "generate returns deterministic vectors for same text" {
            val client = StubEmbeddingClient(dimensions = 8)

            val result1 = client.generate(EmbeddingRequest(listOf("hello")))
            val result2 = client.generate(EmbeddingRequest(listOf("hello")))

            result1.embeddings.size shouldBe 1
            result2.embeddings.size shouldBe 1
            result1.embeddings[0].vector.contentEquals(result2.embeddings[0].vector) shouldBe true
        }

        "generate returns different vectors for different texts" {
            val client = StubEmbeddingClient(dimensions = 8)

            val result = client.generate(EmbeddingRequest(listOf("hello", "world")))

            result.embeddings.size shouldBe 2
            result.embeddings[0].vector.contentEquals(result.embeddings[1].vector) shouldBe false
        }

        "generate returns vectors of correct dimensions" {
            val dimensions = 128
            val client = StubEmbeddingClient(dimensions = dimensions)

            val result = client.generate(EmbeddingRequest(listOf("test")))

            result.embeddings[0].vector.size shouldBe dimensions
        }

        "generate returns correct model" {
            val client = StubEmbeddingClient(model = "custom-model")

            val result = client.generate(EmbeddingRequest(listOf("test")))

            result.model shouldBe "custom-model"
        }

        "healthCheck always returns true" {
            val client = StubEmbeddingClient()

            client.healthCheck() shouldBe true
        }

        "generate preserves text and index in embeddings" {
            val client = StubEmbeddingClient()

            val result = client.generate(EmbeddingRequest(listOf("first", "second")))

            result.embeddings[0].text shouldBe "first"
            result.embeddings[0].index shouldBe 0
            result.embeddings[1].text shouldBe "second"
            result.embeddings[1].index shouldBe 1
        }
    }
})
