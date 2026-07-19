package io.averkhogliad.ai.challenge.week2.unit.bootstrap

import io.averkhogliad.ai.challenge.llm.config.TestConfig
import io.averkhogliad.ai.challenge.week2.bootstrap.ApplicationBootstrap
import io.averkhogliad.ai.challenge.week2.cli.CliApplication
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files

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

    fun minimalConfig(): TestConfig = TestConfig(
        mapOf("app.database.path" to tempDbFile.absolutePath)
    )

    "Application assembly" - {
        "should create valid CliApplication with TodoTaskService" {
            val config = minimalConfig()

            val application = ApplicationBootstrap.createApplication(config)

            application.shouldBeInstanceOf<CliApplication>()
        }

        "should create application with custom database path" {
            val config = TestConfig(
                mapOf("app.database.path" to tempDbFile.absolutePath)
            )

            val application = ApplicationBootstrap.createApplication(config)

            application.shouldBeInstanceOf<CliApplication>()
        }

        "should create application with default database path when not specified" {
            val config = TestConfig(
                mapOf("app.database.path" to tempDbFile.absolutePath)
            )

            val application = ApplicationBootstrap.createApplication(config)

            application.shouldBeInstanceOf<CliApplication>()
        }
    }
})
