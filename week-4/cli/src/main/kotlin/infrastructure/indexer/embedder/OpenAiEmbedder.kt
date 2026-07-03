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
 * Генератор эмбеддингов через OpenAI-совместимый API.
 *
 * Использует endpoint [POST] /v1/embeddings.
 * Совместим с: OpenAI, vLLM, LiteLLM, Azure, корпоративные прокси.
 */
class OpenAiEmbedder(
    private val client: HttpClient,
    private val config: EmbeddingProviderConfig.OpenAi,
    private val common: EmbeddingConfig
) : EmbeddingGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun healthCheck(): Boolean {
        return try {
            // OpenAI не имеет отдельного healthcheck, пробуем models endpoint
            client.post(config.url) {
                contentType(ContentType.Application.Json)
                config.apiKey?.let { header("Authorization", "Bearer $it") }
                setBody(OpenAiEmbedRequest(model = config.model, input = listOf("test")))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun generateBatch(texts: List<Pair<UUID, String>>): List<Embedding> {
        val request = OpenAiEmbedRequest(
            model = config.model,
            input = texts.map { it.second }
        )

        val response = withRetry {
            client.post(config.url) {
                contentType(ContentType.Application.Json)
                config.apiKey?.let { header("Authorization", "Bearer $it") }
                setBody(request)
            }
        }

        val body: OpenAiEmbedResponse = response.body()
        val data = body.data
            ?: throw IllegalStateException("OpenAI returned null data in embeddings response")

        return texts.zip(data).map { (chunkPair, item) ->
            Embedding(
                chunkId = chunkPair.first,
                vector = item.embedding.map { it.toFloat() }.toFloatArray(),
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
    private data class OpenAiEmbedRequest(
        val model: String,
        val input: List<String>
    )

    @Serializable
    private data class OpenAiEmbedResponse(
        val data: List<EmbeddingData>?
    )

    @Serializable
    private data class EmbeddingData(
        val embedding: List<Double>
    )
}
