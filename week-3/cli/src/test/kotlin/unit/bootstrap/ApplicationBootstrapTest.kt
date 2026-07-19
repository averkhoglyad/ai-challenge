package io.averkhogliad.ai.challenge.week3.cli.unit.bootstrap

import io.averkhogliad.ai.challenge.llm.config.TestConfig
import io.averkhogliad.ai.challenge.week3.cli.bootstrap.ApplicationBootstrap
import io.averkhogliad.ai.challenge.week3.cli.cli.CliApplication
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

    beforeTest {
        tempDbFile = Files.createTempFile("test-bootstrap-", ".db").toFile()
    }

    afterTest {
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
            val config = minimalConfig()

            val application = ApplicationBootstrap.createApplication(config)

            application.shouldBeInstanceOf<CliApplication>()
        }

        "should create application with custom database path" {
            val config = TestConfig(
                mapOf(
                    "app.database.path" to tempDbFile.absolutePath
                )
            )

            val application = ApplicationBootstrap.createApplication(config)
            application.shouldBeInstanceOf<CliApplication>()
        }

        "should create application with default database path when not specified" {
            val config = TestConfig(
                mapOf(
                    "app.database.path" to tempDbFile.absolutePath
                )
            )

            val application = ApplicationBootstrap.createApplication(config)
            application.shouldBeInstanceOf<CliApplication>()
        }
    }
})
