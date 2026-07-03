package io.averkhogliad.ai.challenge.week3.cli.it.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPServerConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTransport
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteMCPServerRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.*

/**
 * Интеграционные тесты для [SqliteMCPServerRepository].
 * Использует временный файл SQLite — каждая спецификация получает чистую БД.
 */
class SqliteMCPServerRepositoryIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteMCPServerRepository

    beforeTest {
        tempDbFile = Files.createTempFile("test-mcp-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteMCPServerRepository(database)
    }

    afterTest {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "SqliteMCPServerRepository" - {

        "save and findById" - {
            "should persist a server config and retrieve it by id" {
                val config = createConfig(name = "my-server", transport = MCPTransport.Stdio("echo"))
                runBlocking { repository.save(config) }

                val found = runBlocking { repository.findById(config.id) }
                found shouldNotBe null
                found!!.name shouldBe "my-server"
                found.id shouldBe config.id
                found.enabled shouldBe true
            }

            "should return null when id does not exist" {
                val found = runBlocking { repository.findById(ModelId("nonexistent-id")) }
                found shouldBe null
            }

            "should upsert on re-save with same id" {
                val config = createConfig(name = "original", transport = MCPTransport.Stdio("echo"))
                runBlocking { repository.save(config) }

                val updated = config.copy(name = "updated-name", enabled = false)
                runBlocking { repository.save(updated) }

                val found = runBlocking { repository.findById(config.id) }
                found shouldNotBe null
                found!!.name shouldBe "updated-name"
                found.enabled shouldBe false
            }
        }

        "save duplicate by name" - {
            "should throw exception when saving a config with a duplicate name" {
                val config1 = createConfig(
                    name = "duplicate-name",
                    transport = MCPTransport.Stdio("echo")
                )
                val config2 = createConfig(
                    name = "duplicate-name",
                    transport = MCPTransport.StreamableHttp("http://localhost:8080")
                )
                runBlocking { repository.save(config1) }

                // save with different id but same name → UNIQUE constraint violation
                shouldThrow<IllegalStateException> {
                    runBlocking { repository.save(config2) }
                }

                // old entry still exists (not replaced)
                val found1 = runBlocking { repository.findById(config1.id) }
                found1 shouldNotBe null
                found1!!.name shouldBe "duplicate-name"
                found1.transport shouldBe MCPTransport.Stdio("echo")
                // new entry was never saved
                runBlocking { repository.findById(config2.id) } shouldBe null
            }
        }

        "findByName" - {
            "should find a server config by its name" {
                val config = createConfig(name = "find-me", transport = MCPTransport.Stdio("node"))
                runBlocking { repository.save(config) }

                val found = runBlocking { repository.findByName("find-me") }
                found shouldNotBe null
                found!!.name shouldBe "find-me"
                found.id shouldBe config.id
            }

            "should return null when name does not exist" {
                val found = runBlocking { repository.findByName("no-such-server") }
                found shouldBe null
            }
        }

        "findAll" - {
            "should return an empty list when no servers are stored" {
                val all = runBlocking { repository.findAll() }
                all shouldHaveSize 0
            }

            "should return all saved servers ordered by created_at DESC" {
                val config1 = createConfig(
                    name = "alpha",
                    transport = MCPTransport.Stdio("cmd1"),
                    createdAt = Instant.parse("2025-01-01T00:00:00Z")
                )
                val config2 = createConfig(
                    name = "beta",
                    transport = MCPTransport.Stdio("cmd2"),
                    createdAt = Instant.parse("2025-06-01T00:00:00Z")
                )
                val config3 = createConfig(
                    name = "gamma",
                    transport = MCPTransport.Stdio("cmd3"),
                    createdAt = Instant.parse("2025-03-01T00:00:00Z")
                )
                runBlocking {
                    repository.save(config1)
                    repository.save(config2)
                    repository.save(config3)
                }

                val all = runBlocking { repository.findAll() }
                all shouldHaveSize 3
                // created_at DESC: beta (June) → gamma (March) → alpha (January)
                all[0].name shouldBe "beta"
                all[1].name shouldBe "gamma"
                all[2].name shouldBe "alpha"
            }
        }

        "delete" - {
            "should remove a server config by id" {
                val config = createConfig(name = "delete-me", transport = MCPTransport.Stdio("rm"))
                runBlocking { repository.save(config) }
                runBlocking { repository.findById(config.id) } shouldNotBe null

                runBlocking { repository.delete(config.id) }

                runBlocking { repository.findById(config.id) } shouldBe null
            }

            "should be idempotent when deleting a non-existent id" {
                runBlocking { repository.delete(ModelId("never-saved")) }
                // No exception expected
            }
        }

        "existsByName" - {
            "should return true when the name exists" {
                val config = createConfig(name = "existing", transport = MCPTransport.Stdio("cmd"))
                runBlocking { repository.save(config) }

                runBlocking { repository.existsByName("existing") } shouldBe true
            }

            "should return false when the name does not exist" {
                runBlocking { repository.existsByName("never-created") } shouldBe false
            }

            "should return false when the repository is empty" {
                runBlocking { repository.existsByName("anything") } shouldBe false
            }
        }
    }
})

// ── helpers ────────────────────────────────────────────────────────────────

private fun createConfig(
    name: String,
    transport: MCPTransport,
    enabled: Boolean = true,
    createdAt: Instant = Instant.now()
): MCPServerConfig = MCPServerConfig(
    id = ModelId(UUID.randomUUID().toString()),
    name = name,
    transport = transport,
    enabled = enabled,
    createdAt = createdAt
)
