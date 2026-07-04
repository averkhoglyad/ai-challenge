package io.averkhogliad.ai.challenge.week4.cli.it.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.*
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.history.SqliteQueryHistoryRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * Интеграционные тесты для [SqliteQueryHistoryRepository].
 * Использует временный файл для SQLite базы данных.
 */
class SqliteQueryHistoryRepositoryIT : FreeSpec({

    lateinit var tempDir: Path
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteQueryHistoryRepository

    beforeEach {
        tempDir = Files.createTempDirectory("test-query-history-")
        val dbPath = tempDir.resolve("test-history.db").toString()
        database = SqliteDatabase(dbPath)
        repository = SqliteQueryHistoryRepository(database)
    }

    afterEach {
        database.close()
        try {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        } catch (_: Exception) { /* cleanup best-effort */
        }
    }

    "save and getLast" - {

        "should save entry and return id > 0" {
            runTest {
                val entry = createEntry(query = "test query", answer = "test answer")
                val id = repository.save(entry)
                (id > 0) shouldBe true
            }
        }

        "should retrieve saved entries in reverse chronological order" {
            runTest {
                val now = Instant.now()
                repository.save(createEntry("query-1", "answer-1", timestamp = now.minusSeconds(30)))
                repository.save(createEntry("query-2", "answer-2", timestamp = now.minusSeconds(20)))
                repository.save(createEntry("query-3", "answer-3", timestamp = now.minusSeconds(10)))

                val last = repository.getLast(2)
                last shouldHaveSize 2
                last[0].query shouldBe "query-3"
                last[1].query shouldBe "query-2"
            }
        }

        "should return empty list when no entries" {
            runTest {
                val last = repository.getLast(10)
                last shouldHaveSize 0
            }
        }
    }

    "getById" - {

        "should find entry by id" {
            runTest {
                val id = repository.save(createEntry("find-me", "found"))
                val entry = repository.getById(id)
                (entry != null) shouldBe true
                entry!!.query shouldBe "find-me"
                entry.answer.answer shouldBe "found"
            }
        }

        "should return null for non-existent id" {
            runTest {
                val entry = repository.getById(99999L)
                entry shouldBe null
            }
        }
    }

    "count" - {

        "should return 0 for empty repository" {
            runTest {
                repository.count() shouldBe 0
            }
        }

        "should return correct count after multiple saves" {
            runTest {
                repository.save(createEntry("q1", "a1"))
                repository.save(createEntry("q2", "a2"))
                repository.save(createEntry("q3", "a3"))

                repository.count() shouldBe 3
            }
        }
    }

    "deleteAll" - {

        "should remove all entries" {
            runTest {
                repository.save(createEntry("q1", "a1"))
                repository.save(createEntry("q2", "a2"))
                repository.count() shouldBe 2

                repository.deleteAll()
                repository.count() shouldBe 0
            }
        }

        "should not throw on empty repository" {
            runTest {
                repository.deleteAll()
                repository.count() shouldBe 0
            }
        }
    }

    "table initialization" - {

        "should be idempotent (creating another repo on same DB does not fail)" {
            runTest {
                repository.save(createEntry("test", "data"))
                repository.count() shouldBe 1

                // Create second repository on same database — should not fail
                val repo2 = SqliteQueryHistoryRepository(database)
                repo2.count() shouldBe 1
            }
        }
    }
})

private fun createEntry(
    query: String = "test query",
    answer: String = "test answer",
    mode: SearchMode = SearchMode.Filtered,
    timestamp: Instant = Instant.now()
) = QueryHistoryEntry(
    id = 0,
    query = query,
    answer = RagAnswer(answer = answer),
    searchContext = SearchContext(
        query = query,
        rewrittenQuery = null,
        rawResults = emptyList(),
        filteredResults = emptyList(),
        droppedChunks = emptyList(),
        stats = QueryExecutionStats(
            queryId = UUID.randomUUID(),
            timestamp = timestamp,
            mode = mode,
            totalMs = 150,
            chunks = ChunkFlow(initial = 50, filtered = 25, final = 5),
            score = ScoreDelta(initialAvg = 0.6f, filteredAvg = 0.85f),
            tokens = TokenBreakdown(rewrite = 0, rerank = 0, answer = 200),
            dropped = DropBreakdown(byThreshold = 15, byTopK = 10, byRerank = 0)
        )
    ),
    timestamp = timestamp
)
