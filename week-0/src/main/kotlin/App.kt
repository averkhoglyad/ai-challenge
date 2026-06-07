package io.averkhogliad.ai.challenge.week0

import io.averkhogliad.ai.challenge.utils.config.*
import io.averkhogliad.ai.challenge.utils.llm.LlmClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val CONFIG_FILE_NAME = "application.properties"
private const val USER_CONFIG_DIR = ".ai-challenge"
private const val CLI_CONFIG_PREFIX = "--config="

/**
 * Обязательные ключи конфигурации для работы приложения.
 */
private val REQUIRED_CONFIG_KEYS = listOf(
    "api.base-url",
    "api.key",
    "api.model",
    "api.connect-timeout",
    "api.request-timeout"
)

fun main(args: Array<String>) {
    val userHome = System.getProperty("user.home")
    val userConfigPath = Path.of(userHome, USER_CONFIG_DIR, CONFIG_FILE_NAME)
    
    val provider = ConfigProvider()
        .addSource(ClasspathConfigSource(CONFIG_FILE_NAME))
        .addSource(FileConfigSource(userConfigPath))
        .addSource(FileConfigSource(CONFIG_FILE_NAME))
        .addSource(FileConfigSource("config/$CONFIG_FILE_NAME"))

    // CLI argument: --config=<path> (высший приоритет)
    val cliPath = args
        .firstOrNull { it.startsWith(CLI_CONFIG_PREFIX) }
        ?.removePrefix(CLI_CONFIG_PREFIX)
        ?.takeIf { it.isNotBlank() }

    if (cliPath != null) {
        val path = Paths.get(cliPath)
        require(Files.exists(path)) {
            "Config file specified via $CLI_CONFIG_PREFIX does not exist: $cliPath"
        }
        provider.addSource(FileConfigSource(path, required = true))
    }

    val config = provider.load()
    
    // Валидация обязательных ключей конфигурации
    validateConfig(config)
    
    // Создаём LlmClient один раз для всего приложения
    val llmClient = LlmClient(config)
    
    Menu.mainLoop(config, llmClient)
}

/**
 * Проверяет наличие всех обязательных ключей в конфигурации.
 * 
 * @throws IllegalStateException если отсутствует хотя бы один обязательный ключ
 */
private fun validateConfig(config: Config) {
    val missingKeys = REQUIRED_CONFIG_KEYS.filter { key ->
        config.getOrNull(key).isNullOrBlank()
    }
    
    if (missingKeys.isNotEmpty()) {
        val message = buildString {
            appendLine("Отсутствуют обязательные параметры конфигурации:")
            missingKeys.forEach { key ->
                appendLine("  - $key")
            }
            appendLine()
            appendLine("Укажите их в одном из файлов конфигурации:")
            appendLine("  ~/.ai-challenge/application.properties   (user-level)")
            appendLine("  ./application.properties                 (project-level)")
            appendLine("  ./config/application.properties          (project config dir)")
            appendLine("  --config=/path/to/file.properties        (CLI argument)")
        }
        throw IllegalStateException(message)
    }
}
