package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config

import io.averkhogliad.ai.challenge.utils.config.Config

/**
 * Загружает конфигурацию индексатора из [Config].
 *
 * Это чистая функция-расширение на абстракции [Config] —
 * не зависит от файловой системы.
 */
fun Config.loadIndexerConfig(): IndexerConfig {
    val provider = getOrDefault(KEY_PROVIDER, "ollama").lowercase()

    val batchSize = getOrDefault(KEY_BATCH_SIZE, "16").toIntOrNull()
        ?: throw IllegalArgumentException("Invalid $KEY_BATCH_SIZE: '${getOrNull(KEY_BATCH_SIZE)}'")

    val timeoutSeconds = getOrDefault(KEY_TIMEOUT_SECONDS, "60").toIntOrNull()
        ?: throw IllegalArgumentException("Invalid $KEY_TIMEOUT_SECONDS: '${getOrNull(KEY_TIMEOUT_SECONDS)}'")

    val retryAttempts = getOrDefault(KEY_RETRY_ATTEMPTS, "3").toIntOrNull()
        ?: throw IllegalArgumentException("Invalid $KEY_RETRY_ATTEMPTS: '${getOrNull(KEY_RETRY_ATTEMPTS)}'")

    val retryInitialDelayMs = getOrDefault(KEY_RETRY_INITIAL_DELAY_MS, "1000").toLongOrNull()
        ?: throw IllegalArgumentException("Invalid $KEY_RETRY_INITIAL_DELAY_MS: '${getOrNull(KEY_RETRY_INITIAL_DELAY_MS)}'")

    val chunkSize = getOrDefault(KEY_CHUNK_SIZE, "500").toIntOrNull()
        ?: throw IllegalArgumentException("Invalid $KEY_CHUNK_SIZE: '${getOrNull(KEY_CHUNK_SIZE)}'")

    val overlap = getOrDefault(KEY_CHUNK_OVERLAP, "50").toIntOrNull()
        ?: throw IllegalArgumentException("Invalid $KEY_CHUNK_OVERLAP: '${getOrNull(KEY_CHUNK_OVERLAP)}'")

    val providerConfig = when (provider) {
        "ollama" -> {
            val url = getOrDefault(KEY_OLLAMA_URL, "http://localhost:11434")
            val model = getOrDefault(KEY_OLLAMA_MODEL, "nomic-embed-text")
            EmbeddingProviderConfig.Ollama(url = url, model = model)
        }

        "openai" -> {
            val url = getOrDefault(KEY_OPENAI_URL, "https://api.openai.com/v1/embeddings")
            val model = getOrDefault(KEY_OPENAI_MODEL, "text-embedding-3-small")
            val apiKey = getOrNull(KEY_OPENAI_API_KEY)?.ifBlank { null }
            EmbeddingProviderConfig.OpenAi(url = url, model = model, apiKey = apiKey)
        }

        else -> throw IllegalArgumentException(
            "Unsupported embedding provider: '$provider'. Supported: ollama, openai"
        )
    }

    val embeddingConfig = EmbeddingConfig(
        batchSize = batchSize,
        timeoutSeconds = timeoutSeconds,
        retryAttempts = retryAttempts,
        retryInitialDelayMs = retryInitialDelayMs,
        providerConfig = providerConfig
    )

    return IndexerConfig(
        chunkSize = chunkSize,
        overlap = overlap,
        embedding = embeddingConfig
    )
}

// ──── Config keys ────

private const val KEY_PROVIDER = "indexer.embedding.provider"
private const val KEY_BATCH_SIZE = "indexer.embedding.batch.size"
private const val KEY_TIMEOUT_SECONDS = "indexer.embedding.timeout.seconds"
private const val KEY_RETRY_ATTEMPTS = "indexer.embedding.retry.attempts"
private const val KEY_RETRY_INITIAL_DELAY_MS = "indexer.embedding.retry.initial.delay.ms"
private const val KEY_OLLAMA_URL = "indexer.embedding.ollama.url"
private const val KEY_OLLAMA_MODEL = "indexer.embedding.ollama.model"
private const val KEY_OPENAI_URL = "indexer.embedding.openai.url"
private const val KEY_OPENAI_MODEL = "indexer.embedding.openai.model"
private const val KEY_OPENAI_API_KEY = "indexer.embedding.openai.api-key"
private const val KEY_CHUNK_SIZE = "indexer.chunk.size"
private const val KEY_CHUNK_OVERLAP = "indexer.chunk.overlap"
