package io.averkhogliad.ai.challenge.week4.cli.it.indexer

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.*
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository.IndexerDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository.SqliteIndexRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * Интеграционные тесты для [SqliteIndexRepository].
 * Использует временный файл для SQLite базы данных и проверяет персистентность.
 */
class SqliteIndexRepositoryIT : FreeSpec({

    lateinit var tempDir: Path
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteIndexRepository
    lateinit var dbPath: String

    beforeEach {
        tempDir = Files.createTempDirectory("test-indexer-it-")
        dbPath = tempDir.resolve("test.db").toString()
        database = SqliteDatabase(dbPath)
        IndexerDatabase(database).initialize()
        repository = SqliteIndexRepository(database)
    }

    afterEach {
        database.close()
    }

    // ──── Runs ────

    "should save and find run by id" {
        runTest {
            // given
            val run = createRun(
                id = UUID.randomUUID(),
                sourcePath = "/projects/docs",
                strategy = ChunkingStrategyType.STRUCTURAL
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
        }
    }

    "should return null when run not found" {
        runTest {
            // when
            val found = repository.getRun(UUID.randomUUID())

            // then
            found shouldBe null
        }
    }

    "should persist data across connections" {
        // given
        val runId = UUID.randomUUID()
        val run = createRun(id = runId, sourcePath = "/persist-test")

        runTest {
            repository.createRun(run)
        }

        // Simulate reconnect: close and reopen
        database.close()
        database = SqliteDatabase(dbPath)
        IndexerDatabase(database).initialize()
        repository = SqliteIndexRepository(database)

        runTest {
            // when
            val found = repository.getRun(runId)

            // then — data should survive reconnect
            (found != null) shouldBe true
            found!!.sourcePath shouldBe "/persist-test"
            found.strategy shouldBe ChunkingStrategyType.FIXED_SIZE
        }
    }

    "should find all runs ordered by startedAt DESC" {
        runTest {
            // given
            val r1 =
                createRun(id = UUID.randomUUID(), sourcePath = "/r1", startedAt = Instant.parse("2025-01-01T00:00:00Z"))
            val r2 =
                createRun(id = UUID.randomUUID(), sourcePath = "/r2", startedAt = Instant.parse("2025-02-01T00:00:00Z"))
            val r3 =
                createRun(id = UUID.randomUUID(), sourcePath = "/r3", startedAt = Instant.parse("2025-03-01T00:00:00Z"))
            repository.createRun(r1)
            repository.createRun(r2)
            repository.createRun(r3)

            // when
            val runs = repository.getAllRuns()

            // then — latest first
            runs.size shouldBe 3
            runs[0].sourcePath shouldBe "/r3"
            runs[1].sourcePath shouldBe "/r2"
            runs[2].sourcePath shouldBe "/r1"
        }
    }

    "should update run status" {
        runTest {
            // given
            val run = createRun(id = UUID.randomUUID(), sourcePath = "/status-test", status = RunStatus.RUNNING)
            repository.createRun(run)

            // when
            repository.updateRunStatus(run.id, RunStatus.COMPLETED, totalChunks = 10)
            val updated = repository.getRun(run.id)

            // then
            (updated != null) shouldBe true
            updated!!.status shouldBe RunStatus.COMPLETED
            updated.totalChunks shouldBe 10
            (updated.finishedAt != null) shouldBe true
        }
    }

    "should delete run" {
        runTest {
            // given
            val run = createRun(id = UUID.randomUUID(), sourcePath = "/delete-test")
            repository.createRun(run)

            // when
            repository.deleteRun(run.id)

            // then
            repository.getRun(run.id) shouldBe null
        }
    }

    // ──── Chunks ────

    "should save and retrieve chunks with embeddings" {
        runTest {
            // given
            val runId = UUID.randomUUID()
            val run = createRun(id = runId, sourcePath = "/chunks-test")
            repository.createRun(run)

            val chunk1Id = UUID.randomUUID()
            val chunk2Id = UUID.randomUUID()
            val chunks = listOf(
                makeChunk(chunk1Id, runId, "First chunk text", "/docs/first.md"),
                makeChunk(chunk2Id, runId, "Second chunk text", "/docs/second.md")
            )

            // when
            repository.saveBatch(chunks)
            val retrieved = repository.getChunksByRunId(runId)

            // then
            retrieved.size shouldBe 2
            retrieved[0].chunk.text shouldBe "First chunk text"
            retrieved[1].chunk.text shouldBe "Second chunk text"
            retrieved[0].embedding.vector.size shouldBe 2
            retrieved[1].embedding.vector.size shouldBe 2
        }
    }

    "should persist chunks across connections" {
        // given
        val runId = UUID.randomUUID()
        val chunkId = UUID.randomUUID()

        runTest {
            val run = createRun(id = runId, sourcePath = "/persist-chunks")
            repository.createRun(run)
            repository.saveBatch(
                listOf(
                    makeChunk(chunkId, runId, "Persistent chunk", "/docs/persist.md")
                )
            )
        }

        // Close and reopen
        database.close()
        database = SqliteDatabase(dbPath)
        IndexerDatabase(database).initialize()
        repository = SqliteIndexRepository(database)

        runTest {
            // when
            val retrieved = repository.getChunksByRunId(runId)

            // then
            retrieved.size shouldBe 1
            retrieved[0].chunk.id shouldBe chunkId
            retrieved[0].chunk.text shouldBe "Persistent chunk"
            retrieved[0].embedding.vector.contentEquals(floatArrayOf(0.1f, 0.2f)) shouldBe true
        }
    }

    // ──── Active index ────

    "should set and get active index" {
        runTest {
            // given
            val runId = UUID.randomUUID()
            repository.createRun(createRun(id = runId, sourcePath = "/active-test"))

            // when — initially null
            repository.getActiveIndex() shouldBe null

            // when
            repository.setActiveIndex(runId)
            val active = repository.getActiveIndex()

            // then
            active shouldBe runId
        }
    }

    // ──── Statistics ────

    "should return statistics for run with chunks" {
        runTest {
            // given
            val runId = UUID.randomUUID()
            repository.createRun(
                createRun(id = runId, sourcePath = "/stats-test", strategy = ChunkingStrategyType.FIXED_SIZE)
            )

            repository.saveBatch(
                listOf(
                    makeChunk(UUID.randomUUID(), runId, "short", "/docs/a.md"),
                    makeChunk(UUID.randomUUID(), runId, "longer text", "/docs/a.md"),
                    makeChunk(UUID.randomUUID(), runId, "the longest piece here", "/docs/b.md")
                )
            )

            // when
            val stats = repository.getStatistics(runId)

            // then
            stats.totalChunks shouldBe 3
            stats.strategy shouldBe ChunkingStrategyType.FIXED_SIZE
            (stats.indexSizeBytes > 0) shouldBe true
        }
    }

    // ──── Bulk operations ────

    "should delete runs before date" {
        runTest {
            // given
            val oldRun = createRun(
                id = UUID.randomUUID(),
                sourcePath = "/old",
                startedAt = Instant.parse("2024-06-01T00:00:00Z")
            )
            val newRun = createRun(
                id = UUID.randomUUID(),
                sourcePath = "/new",
                startedAt = Instant.parse("2025-03-01T00:00:00Z")
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

    "should keep last N runs" {
        runTest {
            // given
            val r1 =
                createRun(id = UUID.randomUUID(), sourcePath = "/1", startedAt = Instant.parse("2025-01-01T00:00:00Z"))
            val r2 =
                createRun(id = UUID.randomUUID(), sourcePath = "/2", startedAt = Instant.parse("2025-02-01T00:00:00Z"))
            val r3 =
                createRun(id = UUID.randomUUID(), sourcePath = "/3", startedAt = Instant.parse("2025-03-01T00:00:00Z"))
            val r4 =
                createRun(id = UUID.randomUUID(), sourcePath = "/4", startedAt = Instant.parse("2025-04-01T00:00:00Z"))
            repository.createRun(r1)
            repository.createRun(r2)
            repository.createRun(r3)
            repository.createRun(r4)

            // when
            repository.keepLastRuns(2)

            // then
            val runs = repository.getAllRuns()
            runs.size shouldBe 2
            runs[0].sourcePath shouldBe "/4"
            runs[1].sourcePath shouldBe "/3"
        }
    }

    "should delete all runs except active" {
        runTest {
            // given
            val keepId = UUID.randomUUID()
            repository.createRun(createRun(id = keepId, sourcePath = "/keep"))
            repository.createRun(createRun(id = UUID.randomUUID(), sourcePath = "/remove1"))
            repository.createRun(createRun(id = UUID.randomUUID(), sourcePath = "/remove2"))

            // when
            repository.deleteAllRunsExcept(keepId)

            // then
            val runs = repository.getAllRuns()
            runs.size shouldBe 1
            runs[0].id shouldBe keepId
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
    embeddingModel: String = "nomic-embed-text"
): IndexingRun = IndexingRun(
    id = id,
    startedAt = startedAt,
    finishedAt = if (status != RunStatus.RUNNING) Instant.now() else null,
    strategy = strategy,
    sourcePath = sourcePath,
    chunkSize = if (strategy == ChunkingStrategyType.FIXED_SIZE) 500 else null,
    overlap = if (strategy == ChunkingStrategyType.FIXED_SIZE) 50 else null,
    embeddingModel = embeddingModel,
    status = status,
    totalChunks = 0,
    errorMessage = null,
    metadata = emptyMap()
)

private fun makeChunk(
    chunkId: UUID,
    runId: UUID,
    text: String,
    source: String
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
        vector = floatArrayOf(0.1f, 0.2f),
        model = "test-model"
    )
)
