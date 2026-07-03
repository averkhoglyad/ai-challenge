package io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.embedder

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.EmbeddingConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.EmbeddingProviderConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Embedding
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

/**
 * Генератор эмбеддингов через нативный API Ollama.
 *
 * Использует endpoint [POST] /api/embed.
 */
class OllamaEmbedder(
    private val client: HttpClient,
    private val config: EmbeddingProviderConfig.Ollama,
    private val common: EmbeddingConfig
) : EmbeddingGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun healthCheck(): Boolean {
        return try {
            client.get("${config.url}/api/tags")
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun generateBatch(texts: List<Pair<UUID, String>>): List<Embedding> {
        val request = OllamaEmbedRequest(
            model = config.model,
            input = texts.map { it.second }
        )

        val response = withRetry {
            client.post("${config.url}/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

        val body: OllamaEmbedResponse = response.body()
        val embeddings = body.embeddings ?: throw IllegalStateException("Ollama returned null embeddings")

        return texts.zip(embeddings).map { (chunkPair, vector) ->
            Embedding(
                chunkId = chunkPair.first,
                vector = vector.map { it.toFloat() }.toFloatArray(),
                model = config.model
            )
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        var delayMs = common.retryInitialDelayMs
        repeat(common.retryAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt == common.retryAttempts - 1) throw e
                delay(delayMs)
                delayMs *= 2
            }
        }
        throw lastException ?: IllegalStateException("Retry failed without exception")
    }

    @Serializable
    private data class OllamaEmbedRequest(
        val model: String,
        val input: List<String>
    )

    @Serializable
    private data class OllamaEmbedResponse(
        val embeddings: List<List<Double>>?
    )
}
