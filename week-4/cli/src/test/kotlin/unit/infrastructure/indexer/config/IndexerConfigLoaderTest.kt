package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.indexer.config

import io.averkhogliad.ai.challenge.utils.config.PropertiesConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.EmbeddingProviderConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.loadIndexerConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.util.*

class IndexerConfigLoaderTest : FreeSpec({

    "loadIndexerConfig" - {

        "should load ollama config from properties" {
            // given
            val props = Properties().apply {
                setProperty("indexer.embedding.provider", "ollama")
                setProperty("indexer.embedding.ollama.url", "http://ollama:11434")
                setProperty("indexer.embedding.ollama.model", "nomic-embed-text")
            }
            val config = PropertiesConfig.fromProperties(props)

            // when
            val indexerConfig = config.loadIndexerConfig()

            // then
            indexerConfig.chunkSize shouldBe 500  // default
            indexerConfig.overlap shouldBe 50      // default
            indexerConfig.embedding.provider shouldBe "ollama"
            val providerConfig = indexerConfig.embedding.providerConfig
            (providerConfig is EmbeddingProviderConfig.Ollama) shouldBe true
            val ollama = providerConfig as EmbeddingProviderConfig.Ollama
            ollama.url shouldBe "http://ollama:11434"
            ollama.model shouldBe "nomic-embed-text"
        }

        "should load openai config from properties" {
            // given
            val props = Properties().apply {
                setProperty("indexer.embedding.provider", "openai")
                setProperty("indexer.embedding.openai.url", "https://custom.api.com/embeddings")
                setProperty("indexer.embedding.openai.model", "text-embedding-ada-002")
                setProperty("indexer.embedding.openai.api-key", "sk-my-key")
            }
            val config = PropertiesConfig.fromProperties(props)

            // when
            val indexerConfig = config.loadIndexerConfig()

            // then
            indexerConfig.embedding.provider shouldBe "openai"
            val providerConfig = indexerConfig.embedding.providerConfig
            (providerConfig is EmbeddingProviderConfig.OpenAi) shouldBe true
            val openai = providerConfig as EmbeddingProviderConfig.OpenAi
            openai.url shouldBe "https://custom.api.com/embeddings"
            openai.model shouldBe "text-embedding-ada-002"
            openai.apiKey shouldBe "sk-my-key"
        }

        "should use default values when keys missing" {
            // given
            val props = Properties().apply {
                setProperty("indexer.embedding.provider", "ollama")
            }
            val config = PropertiesConfig.fromProperties(props)

            // when
            val indexerConfig = config.loadIndexerConfig()

            // then
            indexerConfig.chunkSize shouldBe 500
            indexerConfig.overlap shouldBe 50
            indexerConfig.embedding.batchSize shouldBe 16
            indexerConfig.embedding.timeoutSeconds shouldBe 60
            indexerConfig.embedding.retryAttempts shouldBe 3
            indexerConfig.embedding.retryInitialDelayMs shouldBe 1000L
            val ollama = indexerConfig.embedding.providerConfig as EmbeddingProviderConfig.Ollama
            ollama.url shouldBe "http://localhost:11434"
            ollama.model shouldBe "nomic-embed-text"
        }

        "should use default openai config when provider is openai and keys missing" {
            // given
            val props = Properties().apply {
                setProperty("indexer.embedding.provider", "openai")
            }
            val config = PropertiesConfig.fromProperties(props)

            // when
            val indexerConfig = config.loadIndexerConfig()

            // then
            val openai = indexerConfig.embedding.providerConfig as EmbeddingProviderConfig.OpenAi
            openai.url shouldBe "https://api.openai.com/v1/embeddings"
            openai.model shouldBe "text-embedding-3-small"
            openai.apiKey shouldBe null
        }

        "should throw on invalid numeric value for chunk size" {
            // given
            val props = Properties().apply {
                setProperty("indexer.embedding.provider", "ollama")
                setProperty("indexer.chunk.size", "not-a-number")
            }
            val config = PropertiesConfig.fromProperties(props)

            // when & then
            shouldThrow<IllegalArgumentException> {
                config.loadIndexerConfig()
            }
        }

        "should throw on invalid numeric value for batch size" {
            // given
            val props = Properties().apply {
                setProperty("indexer.embedding.provider", "ollama")
                setProperty("indexer.embedding.batch.size", "abc")
            }
            val config = PropertiesConfig.fromProperties(props)

            // when & then
            shouldThrow<IllegalArgumentException> {
                config.loadIndexerConfig()
            }
        }

        "should use ollama as default provider when not specified" {
            // given
            val props = Properties()
            val config = PropertiesConfig.fromProperties(props)

            // when
            val indexerConfig = config.loadIndexerConfig()

            // then
            indexerConfig.embedding.provider shouldBe "ollama"
            (indexerConfig.embedding.providerConfig is EmbeddingProviderConfig.Ollama) shouldBe true
        }

        "should throw on unsupported provider" {
            // given
            val props = Properties().apply {
                setProperty("indexer.embedding.provider", "cohere")
            }
            val config = PropertiesConfig.fromProperties(props)

            // when & then
            shouldThrow<IllegalArgumentException> {
                config.loadIndexerConfig()
            }
        }
    }
})
