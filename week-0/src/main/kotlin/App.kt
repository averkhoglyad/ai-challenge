package io.averkhogliad.ai.challenge.week0

import io.averkhogliad.ai.challenge.llm.config.ClasspathConfigSource
import io.averkhogliad.ai.challenge.llm.config.ConfigProvider
import io.averkhogliad.ai.challenge.llm.config.FileConfigSource
import io.averkhogliad.ai.challenge.week0.bootstrap.ApplicationBootstrap
import io.averkhogliad.ai.challenge.week0.cli.CliApplication
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val CONFIG_FILE_NAME = "application.properties"
private const val USER_CONFIG_DIR = ".ai-challenge"
private const val CLI_CONFIG_PREFIX = "--config="

/**
 * Точка входа приложения AI Challenge.
 *
 * Использует [ApplicationBootstrap] — composition root, который:
 * 1. Загружает конфигурацию через [ConfigProvider]
 * 2. Собирает все компоненты Clean Architecture
 *    (ConfigAdapter → LlmAdapter → domain services → executors → CliApplication)
 */
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

    // ApplicationBootstrap (composition root) создаёт CliApplication
    val app: CliApplication = ApplicationBootstrap.createApplication(config)

    // Запуск приложения с автоматическим освобождением ресурсов
    app.use { it.run(args) }
}
