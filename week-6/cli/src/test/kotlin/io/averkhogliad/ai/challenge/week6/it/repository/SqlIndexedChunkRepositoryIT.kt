package io.averkhogliad.ai.challenge.week6.it.repository

import io.averkhogliad.ai.challenge.indexer.domain.model.Chunk
import io.averkhogliad.ai.challenge.indexer.domain.model.Embedding
import io.averkhogliad.ai.challenge.indexer.domain.model.IndexedChunk
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlIndexedChunkRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.IndexChunksTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectsTable
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class SqlIndexedChunkRepositoryIT : FreeSpec({

    lateinit var dbPath: Path

    fun setupDb(): Database {
        dbPath = Files.createTempFile("test-idx-", ".db")
        val db = Database.connect("jdbc:sqlite:${dbPath.toAbsolutePath()}", "org.sqlite.JDBC")
        transaction(db) { SchemaUtils.create(ProjectsTable, IndexChunksTable) }
        return db
    }

    afterEach {
        Files.deleteIfExists(dbPath)
    }

    "save and findByProjectId" - {

        "saves chunks and retrieves them by project ID" {
            runTest {
                setupDb()
                val repository = SqlIndexedChunkRepository()
                val projectId = "proj-1"
                val chunks = listOf(
                    IndexedChunk(
                        chunk = Chunk(
                            id = UUID.randomUUID(), text = "fun test() { return 42 }",
                            source = "/src/Test.kt",
                        ),
                        embedding = Embedding(
                            chunkId = UUID.randomUUID(), vector = floatArrayOf(0.1f, 0.2f, 0.3f),
                            model = "test-model",
                        ),
                    ),
                    IndexedChunk(
                        chunk = Chunk(
                            id = UUID.randomUUID(), text = "class Foo { val x = 1 }",
                            source = "/src/Foo.kt",
                        ),
                        embedding = Embedding(
                            chunkId = UUID.randomUUID(), vector = floatArrayOf(0.4f, 0.5f, 0.6f),
                            model = "test-model",
                        ),
                    ),
                )

                repository.save(projectId, chunks)
                val result = repository.findByProjectId(projectId)

                result shouldHaveSize 2
                result.map { it.chunk.text } shouldBe listOf("fun test() { return 42 }", "class Foo { val x = 1 }")
                result.map { it.chunk.source } shouldBe listOf("/src/Test.kt", "/src/Foo.kt")
                result.all { it.embedding.model == "test-model" } shouldBe true
            }
        }

        "returns empty list for unknown project" {
            runTest {
                setupDb()
                val repository = SqlIndexedChunkRepository()
                repository.findByProjectId("unknown") shouldHaveSize 0
            }
        }
    }

    "deleteByProjectId" - {

        "removes all chunks for a project" {
            runTest {
                setupDb()
                val repository = SqlIndexedChunkRepository()
                val projectId = "proj-del"
                val chunk = IndexedChunk(
                    chunk = Chunk(id = UUID.randomUUID(), text = "data", source = "file.kt"),
                    embedding = Embedding(chunkId = UUID.randomUUID(), vector = floatArrayOf(1f), model = "m"),
                )
                repository.save(projectId, listOf(chunk))
                repository.findByProjectId(projectId) shouldHaveSize 1

                repository.deleteByProjectId(projectId)
                repository.findByProjectId(projectId) shouldHaveSize 0
            }
        }

        "does not throw for non-existent project" {
            runTest {
                setupDb()
                val repository = SqlIndexedChunkRepository()
                repository.deleteByProjectId("no-such-project")
            }
        }
    }

    "float array roundtrip" - {

        "preserves vector data through save and load" {
            runTest {
                setupDb()
                val repository = SqlIndexedChunkRepository()
                val original = floatArrayOf(0.123f, -0.456f, 0.789f, 1.0f, -0.001f)
                val chunk = IndexedChunk(
                    chunk = Chunk(id = UUID.randomUUID(), text = "roundtrip test", source = "test.kt"),
                    embedding = Embedding(chunkId = UUID.randomUUID(), vector = original, model = "roundtrip-model"),
                )

                repository.save("proj-roundtrip", listOf(chunk))
                val result = repository.findByProjectId("proj-roundtrip")

                result shouldHaveSize 1
                val loaded = result.first().embedding.vector
                loaded.size shouldBe original.size
                loaded[0].toDouble() shouldBe (original[0].toDouble() plusOrMinus 0.001)
                loaded[1].toDouble() shouldBe (original[1].toDouble() plusOrMinus 0.001)
                loaded[4].toDouble() shouldBe (original[4].toDouble() plusOrMinus 0.001)
                result.first().embedding.model shouldBe "roundtrip-model"
            }
        }
    }
})
