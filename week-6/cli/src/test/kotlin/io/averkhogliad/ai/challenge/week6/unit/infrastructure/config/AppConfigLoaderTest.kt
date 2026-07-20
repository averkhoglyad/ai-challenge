package io.averkhogliad.ai.challenge.week6.unit.infrastructure.config

import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingProviderConfig
import io.averkhogliad.ai.challenge.week6.infrastructure.config.AppConfigLoader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class AppConfigLoaderTest : FreeSpec({
    "load" - {
        "loads Ollama embedding settings" {
            withConfig(
                "embedding.provider=OLLAMA",
                "embedding.ollama.url=http://ollama.internal:11434",
                "embedding.ollama.model=bge-m3",
            ) { homeDir ->
                val config = AppConfigLoader(homeDir).load()

                config.embeddingProviderConfig shouldBe EmbeddingProviderConfig.Ollama(
                    baseUrl = "http://ollama.internal:11434",
                    model = "bge-m3",
                )
            }
        }

        "loads OpenAI embedding settings" {
            withConfig(
                "embedding.provider=openai",
                "embedding.openai.url=https://embeddings.example/v1/embeddings",
                "embedding.openai.model=text-embedding-3-large",
                "embedding.openai.api-key=test-api-key",
            ) { homeDir ->
                val config = AppConfigLoader(homeDir).load()

                config.embeddingProviderConfig shouldBe EmbeddingProviderConfig.OpenAi(
                    baseUrl = "https://embeddings.example/v1/embeddings",
                    model = "text-embedding-3-large",
                    apiKey = "test-api-key",
                )
            }
        }

        "rejects unsupported embedding provider" {
            withConfig("embedding.provider=cohere") { homeDir ->
                val error = shouldThrow<IllegalStateException> {
                    AppConfigLoader(homeDir).load()
                }

                error.message shouldBe "Unsupported embedding.provider: 'cohere'. Expected: ollama or openai"
            }
        }
    }
})

private fun withConfig(vararg entries: String, block: (Path) -> Unit) {
    val homeDir = Files.createTempDirectory("week6-app-config-test")
    try {
        val configFile = homeDir.resolve(".ai-challenge/week-6/app.properties")
        Files.createDirectories(configFile.parent)
        val properties = Properties().apply {
            entries.forEach { entry ->
                val (key, value) = entry.split("=", limit = 2)
                setProperty(key, value)
            }
        }
        Files.newBufferedWriter(configFile).use { properties.store(it, null) }

        block(homeDir)
    } finally {
        Files.walk(homeDir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
