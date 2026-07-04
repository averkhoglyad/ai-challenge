package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.QueryHistoryService
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.QueryHistoryRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.*

class QueryHistoryServiceTest : FreeSpec({

    lateinit var repository: QueryHistoryRepository
    lateinit var service: QueryHistoryService

    fun fakeSearchContext(query: String, totalMs: Long, mode: SearchMode = SearchMode.Filtered): SearchContext {
        val stats = QueryExecutionStats(
            queryId = UUID.randomUUID(),
            timestamp = Instant.now(),
            mode = mode,
            totalMs = totalMs,
            chunks = ChunkFlow(initial = 10, filtered = 5, final = 3),
            score = ScoreDelta(initialAvg = 0.6f, filteredAvg = 0.85f),
            tokens = TokenBreakdown(rewrite = null, rerank = null, answer = 400),
            dropped = DropBreakdown(byThreshold = 5, byTopK = 2, byRerank = 0)
        )
        return SearchContext(
            query = query,
            rewrittenQuery = null,
            rawResults = emptyList(),
            filteredResults = emptyList(),
            droppedChunks = emptyList(),
            stats = stats
        )
    }

    fun fakeEntry(id: Long, query: String, totalMs: Long, mode: SearchMode = SearchMode.Filtered): QueryHistoryEntry {
        return QueryHistoryEntry(
            id = id,
            query = query,
            answer = RagAnswer(answer = "answer for $query"),
            searchContext = fakeSearchContext(query, totalMs, mode),
            timestamp = Instant.now()
        )
    }

    beforeEach {
        repository = mockk()
        service = QueryHistoryService(repository)
    }

    "recordQuery" - {

        "saves and returns id" {
            runTest {
                // given
                val answer = RagAnswer(answer = "test answer")
                val ctx = fakeSearchContext("test query", 1000)
                coEvery { repository.save(any()) } returns 42L

                // when
                val id = service.recordQuery("test query", answer, ctx)

                // then
                id shouldBe 42L
                coVerify { repository.save(any()) }
            }
        }
    }

    "getLast" - {

        "delegates to repository" {
            runTest {
                // given
                val entries = listOf(fakeEntry(1, "q1", 1000))
                coEvery { repository.getLast(5) } returns entries

                // when
                val result = service.getLast(5)

                // then
                result shouldHaveSize 1
                result[0].query shouldBe "q1"
            }
        }
    }

    "getDetailed" - {

        "delegates to repository" {
            runTest {
                // given
                val entry = fakeEntry(1, "q1", 1000)
                coEvery { repository.getById(1L) } returns entry

                // when
                val result = service.getDetailed(1L)

                // then
                result shouldBe entry
            }
        }
    }

    "clearHistory" - {

        "delegates to repository" {
            runTest {
                coEvery { repository.deleteAll() } returns Unit

                // when
                service.clearHistory()

                // then
                coVerify { repository.deleteAll() }
            }
        }
    }

    "getAggregatedStats" - {

        "returns correct aggregations" {
            runTest {
                // given
                val entries = listOf(
                    fakeEntry(1, "q1", 1000, SearchMode.Filtered),
                    fakeEntry(2, "q2", 2000, SearchMode.Filtered),
                    fakeEntry(3, "q3", 3500, SearchMode.Reranked)
                )
                coEvery { repository.getLast(50) } returns entries

                // when
                val stats = service.getAggregatedStats(50)

                // then
                stats.totalQueries shouldBe 3
                stats.byMode.keys shouldHaveSize 2
                stats.byMode[SearchMode.Filtered]!!.count shouldBe 2
                stats.byMode[SearchMode.Reranked]!!.count shouldBe 1
                stats.avgTotalTimeMs shouldBe 2166L // (1000+2000+3500)/3 ≈ 2166
            }
        }

        "returns empty stats when no queries" {
            runTest {
                // given
                coEvery { repository.getLast(50) } returns emptyList()

                // when
                val stats = service.getAggregatedStats(50)

                // then
                stats.totalQueries shouldBe 0
                stats.byMode shouldBe emptyMap()
                stats.avgTotalTimeMs shouldBe 0L
            }
        }
    }
})
