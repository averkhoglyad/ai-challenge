package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.indexer.repository

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.*
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository.IndexerDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository.SqliteIndexRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

class SqliteIndexRepositoryTest : FreeSpec({

    lateinit var tempDir: Path
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteIndexRepository

    beforeEach {
        tempDir = Files.createTempDirectory("test-indexer-unit-")
        val dbPath = tempDir.resolve("test.db").toString()
        database = SqliteDatabase(dbPath)
        IndexerDatabase(database).initialize()
        repository = SqliteIndexRepository(database)
    }

    afterEach {
        database.close()
    }

    // ──── Runs ────

    "createRun + getRun" - {

        "should round-trip an IndexingRun" {
            runTest {
                // given
                val run = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/docs",
                    strategy = ChunkingStrategyType.FIXED_SIZE,
                    status = RunStatus.RUNNING
                )

                // when
                repository.createRun(run)
                val found = repository.getRun(run.id)

                // then
                (found != null) shouldBe true
                found!!.id shouldBe run.id
                found.sourcePath shouldBe run.sourcePath
                found.strategy shouldBe run.strategy
                found.status shouldBe run.status
                found.embeddingModel shouldBe run.embeddingModel
                found.totalChunks shouldBe run.totalChunks
            }
        }

        "should return null for non-existent run" {
            runTest {
                // when
                val found = repository.getRun(UUID.randomUUID())

                // then
                found shouldBe null
            }
        }
    }

    "getAllRuns" - {

        "should return runs ordered by startedAt DESC" {
            runTest {
                // given
                val earlyRun = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/old",
                    startedAt = Instant.parse("2025-01-01T00:00:00Z")
                )
                val lateRun = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/new",
                    startedAt = Instant.parse("2025-02-01T00:00:00Z")
                )
                repository.createRun(earlyRun)
                repository.createRun(lateRun)

                // when
                val runs = repository.getAllRuns()

                // then — latest first
                runs.size shouldBe 2
                runs[0].sourcePath shouldBe "/new"
                runs[1].sourcePath shouldBe "/old"
            }
        }
    }

    "updateRunStatus" - {

        "should change status and set finishedAt" {
            runTest {
                // given
                val run = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/docs",
                    status = RunStatus.RUNNING
                )
                repository.createRun(run)

                // when
                repository.updateRunStatus(
                    runId = run.id,
                    status = RunStatus.COMPLETED,
                    totalChunks = 42
                )
                val updated = repository.getRun(run.id)

                // then
                (updated != null) shouldBe true
                updated!!.status shouldBe RunStatus.COMPLETED
                updated.totalChunks shouldBe 42
                updated.finishedAt shouldBe (updated.finishedAt ?: error("finishedAt should not be null"))
            }
        }

        "should set errorMessage when status is FAILED" {
            runTest {
                // given
                val run = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/docs",
                    status = RunStatus.RUNNING
                )
                repository.createRun(run)

                // when
                repository.updateRunStatus(
                    runId = run.id,
                    status = RunStatus.FAILED,
                    errorMessage = "Something went wrong"
                )
                val updated = repository.getRun(run.id)

                // then
                (updated != null) shouldBe true
                updated!!.status shouldBe RunStatus.FAILED
                updated.errorMessage shouldBe "Something went wrong"
            }
        }
    }

    "deleteRun" - {

        "should remove run and cascade to chunks" {
            runTest {
                // given
                val runId = UUID.randomUUID()
                val run = createRun(id = runId, sourcePath = "/docs")
                repository.createRun(run)

                val chunkData = IndexedChunk(
                    chunk = Chunk(
                        id = UUID.randomUUID(),
                        runId = runId,
                        contentHash = "hash",
                        source = "/docs",
                        title = "test",
                        section = null,
                        text = "test text",
                        strategy = ChunkingStrategyType.FIXED_SIZE,
                        metadata = emptyMap()
                    ),
                    embedding = Embedding(
                        chunkId = UUID.randomUUID(),
                        vector = floatArrayOf(1.0f),
                        model = "test-model"
                    )
                )
                repository.saveBatch(listOf(chunkData))

                // when
                repository.deleteRun(runId)

                // then
                repository.getRun(runId) shouldBe null
                val chunks = repository.getChunksByRunId(runId)
                chunks.isEmpty() shouldBe true
            }
        }
    }

    // ──── Active index ────

    "setActiveIndex + getActiveIndex" - {

        "should round-trip active index" {
            runTest {
                // given
                val runId = UUID.randomUUID()
                val run = createRun(id = runId, sourcePath = "/docs")
                repository.createRun(run)

                // when — initially null
                val before = repository.getActiveIndex()
                // then
                before shouldBe null

                // when — set and retrieve
                repository.setActiveIndex(runId)
                val after = repository.getActiveIndex()

                // then
                after shouldBe runId
            }
        }
    }

    // ──── Chunks ────

    "saveBatch + getChunksByRunId" - {

        "should round-trip batches of IndexedChunks" {
            runTest {
                // given
                val runId = UUID.randomUUID()
                val run = createRun(id = runId, sourcePath = "/docs")
                repository.createRun(run)

                val chunk1Id = UUID.randomUUID()
                val chunk2Id = UUID.randomUUID()
                val chunks = listOf(
                    IndexedChunk(
                        chunk = Chunk(
                            id = chunk1Id,
                            runId = runId,
                            contentHash = "hash1",
                            source = "/docs/a.md",
                            title = "a.md",
                            section = "## Intro",
                            text = "Hello world",
                            strategy = ChunkingStrategyType.STRUCTURAL,
                            metadata = mapOf("key" to "value")
                        ),
                        embedding = Embedding(
                            chunkId = chunk1Id,
                            vector = floatArrayOf(0.1f, 0.2f),
                            model = "nomic-embed-text"
                        )
                    ),
                    IndexedChunk(
                        chunk = Chunk(
                            id = chunk2Id,
                            runId = runId,
                            contentHash = "hash2",
                            source = "/docs/b.md",
                            title = "b.md",
                            section = "## Summary",
                            text = "Goodbye world",
                            strategy = ChunkingStrategyType.STRUCTURAL,
                            metadata = emptyMap()
                        ),
                        embedding = Embedding(
                            chunkId = chunk2Id,
                            vector = floatArrayOf(0.3f, 0.4f),
                            model = "nomic-embed-text"
                        )
                    )
                )

                // when
                repository.saveBatch(chunks)
                val retrieved = repository.getChunksByRunId(runId)

                // then
                retrieved.size shouldBe 2
                retrieved[0].chunk.text shouldBe "Hello world"
                retrieved[1].chunk.text shouldBe "Goodbye world"
                retrieved[0].embedding.vector.contentEquals(floatArrayOf(0.1f, 0.2f)) shouldBe true
                retrieved[1].embedding.vector.contentEquals(floatArrayOf(0.3f, 0.4f)) shouldBe true
            }
        }
    }

    "getStatistics" - {

        "should return correct aggregates" {
            runTest {
                // given
                val runId = UUID.randomUUID()
                val run = createRun(id = runId, sourcePath = "/docs", strategy = ChunkingStrategyType.STRUCTURAL)
                repository.createRun(run)

                val chunk1Id = UUID.randomUUID()
                val chunk2Id = UUID.randomUUID()
                val chunk3Id = UUID.randomUUID()
                val chunks = listOf(
                    makeChunk(chunk1Id, runId, "short", source = "/docs/a.md"),
                    makeChunk(chunk2Id, runId, "medium length text here", source = "/docs/a.md"),
                    makeChunk(chunk3Id, runId, "a much longer piece of text for testing", source = "/docs/b.md")
                )
                repository.saveBatch(chunks)

                // when
                val stats = repository.getStatistics(runId)

                // then
                stats.totalChunks shouldBe 3
                stats.strategy shouldBe ChunkingStrategyType.STRUCTURAL
                stats.sourcePath shouldBe "/docs"
                stats.bySource["/docs/a.md"] shouldBe 2
                stats.bySource["/docs/b.md"] shouldBe 1
                stats.minChunkSize shouldBe 5  // "short".length
                stats.maxChunkSize shouldBe "a much longer piece of text for testing".length
            }
        }

        "should throw when no chunks exist" {
            runTest {
                // given
                val runId = UUID.randomUUID()
                val run = createRun(id = runId, sourcePath = "/docs")
                repository.createRun(run)

                // when & then
                shouldThrow<NoSuchElementException> {
                    repository.getStatistics(runId)
                }
            }
        }
    }

    // ──── Bulk delete operations ────

    "deleteRunsBefore" - {

        "should remove runs older than given date" {
            runTest {
                // given
                val oldRun = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/old",
                    startedAt = Instant.parse("2024-01-01T00:00:00Z")
                )
                val newRun = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/new",
                    startedAt = Instant.parse("2025-06-01T00:00:00Z")
                )
                repository.createRun(oldRun)
                repository.createRun(newRun)

                // when
                repository.deleteRunsBefore(Instant.parse("2025-01-01T00:00:00Z"))

                // then
                repository.getRun(oldRun.id) shouldBe null
                (repository.getRun(newRun.id) != null) shouldBe true
            }
        }
    }

    "keepLastRuns" - {

        "should keep only the last N runs" {
            runTest {
                // given
                val r1 = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/1",
                    startedAt = Instant.parse("2025-01-01T00:00:00Z")
                )
                val r2 = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/2",
                    startedAt = Instant.parse("2025-02-01T00:00:00Z")
                )
                val r3 = createRun(
                    id = UUID.randomUUID(),
                    sourcePath = "/3",
                    startedAt = Instant.parse("2025-03-01T00:00:00Z")
                )
                repository.createRun(r1)
                repository.createRun(r2)
                repository.createRun(r3)

                // when
                repository.keepLastRuns(2)

                // then — only the 2 most recent remain
                val runs = repository.getAllRuns()
                runs.size shouldBe 2
                runs[0].sourcePath shouldBe "/3"
                runs[1].sourcePath shouldBe "/2"
            }
        }
    }

    "deleteAllRunsExcept" - {

        "should delete all runs except the specified one" {
            runTest {
                // given
                val keepId = UUID.randomUUID()
                val removeId1 = UUID.randomUUID()
                val removeId2 = UUID.randomUUID()
                repository.createRun(
                    createRun(
                        id = keepId,
                        sourcePath = "/keep",
                        startedAt = Instant.parse("2025-03-01T00:00:00Z")
                    )
                )
                repository.createRun(
                    createRun(
                        id = removeId1,
                        sourcePath = "/remove1",
                        startedAt = Instant.parse("2025-01-01T00:00:00Z")
                    )
                )
                repository.createRun(
                    createRun(
                        id = removeId2,
                        sourcePath = "/remove2",
                        startedAt = Instant.parse("2025-02-01T00:00:00Z")
                    )
                )

                // when
                repository.deleteAllRunsExcept(keepId)

                // then
                val runs = repository.getAllRuns()
                runs.size shouldBe 1
                runs[0].id shouldBe keepId
            }
        }

        "should delete all runs when activeRunId is null" {
            runTest {
                // given
                repository.createRun(createRun(id = UUID.randomUUID(), sourcePath = "/a"))
                repository.createRun(createRun(id = UUID.randomUUID(), sourcePath = "/b"))

                // when
                repository.deleteAllRunsExcept(null)

                // then
                val runs = repository.getAllRuns()
                runs.size shouldBe 0
            }
        }
    }
})

// ──── Test helpers ────

private fun createRun(
    id: UUID,
    sourcePath: String,
    strategy: ChunkingStrategyType = ChunkingStrategyType.FIXED_SIZE,
    status: RunStatus = RunStatus.COMPLETED,
    startedAt: Instant = Instant.now(),
    finishedAt: Instant? = null,
    embeddingModel: String = "nomic-embed-text",
    totalChunks: Int = 0,
    errorMessage: String? = null
): IndexingRun = IndexingRun(
    id = id,
    startedAt = startedAt,
    finishedAt = finishedAt,
    strategy = strategy,
    sourcePath = sourcePath,
    chunkSize = if (strategy == ChunkingStrategyType.FIXED_SIZE) 500 else null,
    overlap = if (strategy == ChunkingStrategyType.FIXED_SIZE) 50 else null,
    embeddingModel = embeddingModel,
    status = status,
    totalChunks = totalChunks,
    errorMessage = errorMessage,
    metadata = emptyMap()
)

private fun makeChunk(
    chunkId: UUID,
    runId: UUID,
    text: String,
    source: String = "/docs/test.md"
): IndexedChunk = IndexedChunk(
    chunk = Chunk(
        id = chunkId,
        runId = runId,
        contentHash = "hash-${chunkId}",
        source = source,
        title = source.substringAfterLast('/'),
        section = null,
        text = text,
        strategy = ChunkingStrategyType.STRUCTURAL,
        metadata = emptyMap()
    ),
    embedding = Embedding(
        chunkId = chunkId,
        vector = floatArrayOf(1.0f),
        model = "test-model"
    )
)
