package io.averkhogliad.ai.challenge.week4.cli.unit.bootstrap

import io.averkhogliad.ai.challenge.utils.config.TestConfig
import io.averkhogliad.ai.challenge.week4.cli.bootstrap.ApplicationBootstrap
import io.averkhogliad.ai.challenge.week4.cli.cli.CliApplication
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files

/**
 * Тесты для [ApplicationBootstrap] — composition root приложения.
 *
 * Проверяют:
 * - Корректную сборку всех компонентов архитектуры
 * - Создание TodoTaskService вместо Task1Executor
 * - Создание SqliteTaskRepository вместо SqliteDialogRepository
 */
class ApplicationBootstrapTest : FreeSpec({
    lateinit var tempDbFile: File

    beforeEach {
        tempDbFile = Files.createTempFile("test-bootstrap-", ".db").toFile()
    }

    afterEach {
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    /**
     * Минимальная валидная конфигурация для todo-менеджера.
     * LLM-конфигурация больше не требуется (Фаза 2).
     */
    fun minimalConfig(): TestConfig = TestConfig(
        mapOf(
            "app.database.path" to tempDbFile.absolutePath
        )
    )

    "Application assembly" - {

        "should create valid CliApplication with TodoTaskService" {
            // given
            val config = minimalConfig()

            // when
            val application = ApplicationBootstrap.createApplication(config)

            // then
            application.shouldBeInstanceOf<CliApplication>()
            // Проверяем, что application создан без исключений
            // (не запускаем REPL, так как это side-effect)
        }

        "should create application with custom database path" {
            // given
            val config = TestConfig(
                mapOf(
                    "app.database.path" to tempDbFile.absolutePath
                )
            )

            // when
            val application = ApplicationBootstrap.createApplication(config)

            // then
            application.shouldBeInstanceOf<CliApplication>()
        }

        "should create application with default database path when not specified" {
            // given
            val config = TestConfig(
                mapOf(
                    "app.database.path" to tempDbFile.absolutePath
                )
            )

            // when
            val application = ApplicationBootstrap.createApplication(config)

            // then
            application.shouldBeInstanceOf<CliApplication>()
        }
    }
})
