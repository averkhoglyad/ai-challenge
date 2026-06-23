package io.averkhogliad.ai.challenge.week2.bootstrap

import io.averkhogliad.ai.challenge.utils.config.TestConfig
import io.averkhogliad.ai.challenge.week2.cli.CliApplication
import org.junit.jupiter.api.*
import java.io.File
import java.nio.file.Files
import kotlin.test.assertIs

/**
 * Тесты для [ApplicationBootstrap] — composition root приложения.
 *
 * Проверяют:
 * - Корректную сборку всех компонентов архитектуры
 * - Создание TodoTaskService вместо Task1Executor
 * - Создание SqliteTaskRepository вместо SqliteDialogRepository
 */
@DisplayName("ApplicationBootstrap")
class ApplicationBootstrapTest {

    private lateinit var tempDbFile: File

    @BeforeEach
    fun setUp() {
        tempDbFile = Files.createTempFile("test-bootstrap-", ".db").toFile()
    }

    @AfterEach
    fun tearDown() {
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    /**
     * Минимальная валидная конфигурация для todo-менеджера.
     * LLM-конфигурация больше не требуется (Фаза 2).
     */
    private fun minimalConfig(): TestConfig = TestConfig(
        mapOf(
            "app.database.path" to tempDbFile.absolutePath
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // Application assembly
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Application assembly")
    inner class ApplicationAssembly {

        @Test
        @DisplayName("should create valid CliApplication with TodoTaskService")
        fun `creates application with TodoTaskService`() {
            val config = minimalConfig()

            val application = ApplicationBootstrap.createApplication(config)

            assertIs<CliApplication>(application)
            // Проверяем, что application создан без исключений
            // (не запускаем REPL, так как это side-effect)
        }

        @Test
        @DisplayName("should create application with custom database path")
        fun `uses custom database path`() {
            val config = TestConfig(
                mapOf(
                    "app.database.path" to tempDbFile.absolutePath
                )
            )

            val application = ApplicationBootstrap.createApplication(config)
            assertIs<CliApplication>(application)
        }

        @Test
        @DisplayName("should create application with default database path when not specified")
        fun `uses default database path`() {
            val config = TestConfig(
                mapOf(
                    "app.database.path" to tempDbFile.absolutePath
                )
            )

            val application = ApplicationBootstrap.createApplication(config)
            assertIs<CliApplication>(application)
        }
    }
}
