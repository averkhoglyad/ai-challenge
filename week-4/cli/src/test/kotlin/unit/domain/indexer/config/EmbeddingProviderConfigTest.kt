package io.averkhogliad.ai.challenge.week4.cli.unit.domain.indexer.config

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.EmbeddingProviderConfig
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class EmbeddingProviderConfigTest : FreeSpec({

    "sealed class hierarchy" - {

        "should have Ollama and OpenAi as distinct subtypes" {
            // given
            val ollama: EmbeddingProviderConfig = EmbeddingProviderConfig.Ollama(
                url = "http://localhost:11434",
                model = "nomic-embed-text"
            )
            val openai: EmbeddingProviderConfig = EmbeddingProviderConfig.OpenAi(
                url = "https://api.openai.com/v1/embeddings",
                model = "text-embedding-3-small",
                apiKey = "sk-test-key"
            )

            // when & then
            (ollama is EmbeddingProviderConfig.Ollama) shouldBe true
            (ollama is EmbeddingProviderConfig.OpenAi) shouldBe false
            (openai is EmbeddingProviderConfig.Ollama) shouldBe false
            (openai is EmbeddingProviderConfig.OpenAi) shouldBe true
        }

        "should not be equal to each other" {
            // given
            val ollama: EmbeddingProviderConfig = EmbeddingProviderConfig.Ollama(
                url = "http://localhost:11434",
                model = "nomic-embed-text"
            )
            val openai: EmbeddingProviderConfig = EmbeddingProviderConfig.OpenAi(
                url = "https://api.openai.com/v1/embeddings",
                model = "text-embedding-3-small",
                apiKey = "sk-key"
            )

            // when & then — they are different types
            (ollama == openai) shouldBe false
        }

        "exhaustive when should compile for both branches" {
            // given
            val configs: List<EmbeddingProviderConfig> = listOf(
                EmbeddingProviderConfig.Ollama("url", "model"),
                EmbeddingProviderConfig.OpenAi("url", "model", null)
            )

            // when — simulate exhaustive when
            val results = configs.map { config ->
                when (config) {
                    is EmbeddingProviderConfig.Ollama -> "ollama:${config.model}"
                    is EmbeddingProviderConfig.OpenAi -> "openai:${config.model}"
                }
            }

            // then
            results shouldBe listOf("ollama:model", "openai:model")
        }
    }
})
