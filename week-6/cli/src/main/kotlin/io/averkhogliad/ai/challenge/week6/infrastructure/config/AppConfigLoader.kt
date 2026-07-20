package io.averkhogliad.ai.challenge.week6.infrastructure.config

import io.averkhogliad.ai.challenge.llm.config.*
import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingProviderConfig
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class AppConfigLoader(
    private val homeDir: Path = Path.of(System.getProperty("user.home")),
) {

    companion object {
        private const val DEFAULT_DB_PATH = "copilot.db"
        private const val CONFIG_DIR = ".ai-challenge"
        private const val CONFIG_SUBDIR = "week-6"
        private const val CONFIG_FILE = "app.properties"
        private const val RESOURCE_CONFIG = "app.properties"

        const val KEY_DB_PATH = "db.path"
        const val KEY_LLM_BASE_URL = "llm.baseUrl"
        const val KEY_LLM_API_KEY = "llm.apiKey"
        const val KEY_LLM_MODEL = "llm.model"
        const val KEY_EMBEDDING_PROVIDER = "embedding.provider"
        const val KEY_EMBEDDING_OLLAMA_URL = "embedding.ollama.url"
        const val KEY_EMBEDDING_OLLAMA_MODEL = "embedding.ollama.model"
        const val KEY_EMBEDDING_OPENAI_URL = "embedding.openai.url"
        const val KEY_EMBEDDING_OPENAI_MODEL = "embedding.openai.model"
        const val KEY_EMBEDDING_OPENAI_API_KEY = "embedding.openai.api-key"

        private const val DEFAULT_LLM_BASE_URL = "http://localhost:11434/v1"
        private const val DEFAULT_LLM_API_KEY = "ollama"
        private const val DEFAULT_LLM_MODEL = "qwen2.5:7b"
    }

    private val configDir: Path = homeDir.resolve(CONFIG_DIR).resolve(CONFIG_SUBDIR)

    fun load(): AppConfig {
        val configFile = configDir.resolve(CONFIG_FILE)
        ensureConfigExists(configFile)

        // Cascading override: resource defaults → user file → env vars (LLM_ prefix)
        val config: Config = ConfigProvider()
            .addSource(ClasspathConfigSource(RESOURCE_CONFIG))
            .addSource(FileConfigSource(configFile))
            .addSource(EnvConfigSource("LLM_"))
            .load()

        val dbPath = config.getOrDefault(KEY_DB_PATH, configDir.resolve(DEFAULT_DB_PATH).toString())
        val llmBaseUrl = config.getOrDefault(KEY_LLM_BASE_URL, DEFAULT_LLM_BASE_URL)
        val llmApiKey = config.getOrDefault(KEY_LLM_API_KEY, DEFAULT_LLM_API_KEY)
        val llmModel = config.getOrDefault(KEY_LLM_MODEL, DEFAULT_LLM_MODEL)
        val embeddingProvider = config.getOrDefault(KEY_EMBEDDING_PROVIDER, "ollama")
            .trim()
            .lowercase()
        val embeddingConfig = when (embeddingProvider) {
            "ollama" -> EmbeddingProviderConfig.Ollama(
                baseUrl = config.getOrDefault(KEY_EMBEDDING_OLLAMA_URL, "http://localhost:11434"),
                model = config.getOrDefault(KEY_EMBEDDING_OLLAMA_MODEL, "nomic-embed-text"),
            )

            "openai" -> EmbeddingProviderConfig.OpenAi(
                baseUrl = config.getOrDefault(KEY_EMBEDDING_OPENAI_URL, "https://api.openai.com/v1/embeddings"),
                model = config.getOrDefault(KEY_EMBEDDING_OPENAI_MODEL, "text-embedding-3-small"),
                apiKey = config.getOrDefault(KEY_EMBEDDING_OPENAI_API_KEY, ""),
            )

            else -> error("Unsupported embedding.provider: '$embeddingProvider'. Expected: ollama or openai")
        }

        return AppConfig(
            dbPath = Path.of(dbPath),
            llmBaseUrl = llmBaseUrl,
            llmApiKey = llmApiKey,
            llmModel = llmModel,
            embeddingProviderConfig = embeddingConfig,
        )
    }

    private fun ensureConfigExists(configFile: Path) {
        if (!Files.exists(configFile)) {
            Files.createDirectories(configDir)
            val defaultProps = Properties().apply {
                setProperty(KEY_DB_PATH, configDir.resolve(DEFAULT_DB_PATH).toString())
            }
            Files.newBufferedWriter(configFile).use { writer ->
                defaultProps.store(writer, "AI Challenge Week-6 Configuration")
            }
        }
    }
}

data class AppConfig(
    val dbPath: Path,
    val llmBaseUrl: String,
    val llmApiKey: String,
    val llmModel: String,
    val embeddingProviderConfig: EmbeddingProviderConfig,
)
