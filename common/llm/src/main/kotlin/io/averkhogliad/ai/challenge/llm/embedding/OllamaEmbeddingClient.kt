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

class OllamaEmbeddingClient(
    private val config: EmbeddingProviderConfig.Ollama,
    private val httpClient: HttpClient = defaultHttpClient(config),
) : EmbeddingClient {

    override val model: String = config.model
    override val dimensions: Int = config.dimensions

    override suspend fun generate(request: EmbeddingRequest): EmbeddingResponse {
        val requestBody = OllamaEmbedRequest(
            model = request.model ?: config.model,
            input = request.texts,
        )

        val response = httpClient.post("${config.baseUrl}/api/embed") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val responseText = response.bodyAsText()
        val embedResponse = json.decodeFromString<OllamaEmbedResponse>(responseText)

        val embeddings = embedResponse.embeddings.mapIndexed { index, vector ->
            LlmEmbedding(
                text = request.texts[index],
                vector = vector.toFloatArray(),
                index = index,
            )
        }

        return EmbeddingResponse(embeddings, embedResponse.model, null)
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            httpClient.get("${config.baseUrl}/api/tags")
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun close() {
        httpClient.close()
    }

    @Serializable
    private data class OllamaEmbedRequest(
        val model: String,
        val input: List<String>,
    )

    @Serializable
    private data class OllamaEmbedResponse(
        val model: String,
        val embeddings: List<List<Float>>,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun defaultHttpClient(config: EmbeddingProviderConfig.Ollama): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = config.timeout.inWholeMilliseconds
            }
        }
    }
}
