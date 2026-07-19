package io.averkhogliad.ai.challenge.week6.infrastructure.config

import io.averkhogliad.ai.challenge.llm.config.Config
import io.averkhogliad.ai.challenge.llm.config.ConfigProvider
import io.averkhogliad.ai.challenge.llm.config.FileConfigSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class AppConfigLoader {

    companion object {
        private const val DEFAULT_DB_PATH = "copilot.db"
        private const val CONFIG_DIR = ".ai-challenge"
        private const val CONFIG_SUBDIR = "week-6"
        private const val CONFIG_FILE = "app.properties"

        const val KEY_DB_PATH = "db.path"
    }

    private val homeDir: Path = Path.of(System.getProperty("user.home"))
    private val configDir: Path = homeDir.resolve(CONFIG_DIR).resolve(CONFIG_SUBDIR)

    fun load(): AppConfig {
        val configFile = configDir.resolve(CONFIG_FILE)
        ensureConfigExists(configFile)

        val config: Config = ConfigProvider()
            .addSource(FileConfigSource(configFile))
            .load()

        val dbPath = config.getOrDefault(KEY_DB_PATH, configDir.resolve(DEFAULT_DB_PATH).toString())
        return AppConfig(dbPath = Path.of(dbPath))
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
)
