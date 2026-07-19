package io.averkhogliad.ai.challenge.llm.embedding

import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingProviderConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenAiEmbeddingClient(
    private val config: EmbeddingProviderConfig.OpenAi,
    private val httpClient: HttpClient = defaultHttpClient(config),
) : EmbeddingClient {

    override val model: String = config.model
    override val dimensions: Int = config.dimensions

    override suspend fun generate(request: EmbeddingRequest): EmbeddingResponse {
        val requestBody = OpenAiEmbedRequest(
            model = request.model ?: config.model,
            input = request.texts,
        )

        val response = httpClient.post(config.baseUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.apiKey}")
            setBody(requestBody)
        }

        val responseText = response.bodyAsText()
        val embedResponse = json.decodeFromString<OpenAiEmbedResponse>(responseText)

        val embeddings = embedResponse.data.map { item ->
            LlmEmbedding(
                text = request.texts[item.index],
                vector = item.embedding.toFloatArray(),
                index = item.index,
            )
        }

        val usage = embedResponse.usage?.let {
            TokenUsage(promptTokens = it.promptTokens, totalTokens = it.totalTokens)
        }

        return EmbeddingResponse(embeddings, embedResponse.model, usage)
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            val modelsUrl = config.baseUrl.removeSuffix("/embeddings") + "/models"
            httpClient.get(modelsUrl) {
                header("Authorization", "Bearer ${config.apiKey}")
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun close() {
        httpClient.close()
    }

    @Serializable
    private data class OpenAiEmbedRequest(
        val model: String,
        val input: List<String>,
    )

    @Serializable
    private data class OpenAiEmbedResponse(
        val `data`: List<EmbeddingData>,
        val model: String,
        val usage: OpenAiUsage? = null,
    )

    @Serializable
    private data class EmbeddingData(
        val embedding: List<Float>,
        val index: Int,
    )

    @Serializable
    private data class OpenAiUsage(
        val promptTokens: Int,
        val totalTokens: Int,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun defaultHttpClient(config: EmbeddingProviderConfig.OpenAi): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = config.timeout.inWholeMilliseconds
            }
        }
    }
}
