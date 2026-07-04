package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.rag.rerank

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.DropReason
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rerank.ThresholdReranker
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.util.*

class ThresholdRerankerTest : FreeSpec({

    val runId = UUID.randomUUID()
    val reranker = ThresholdReranker()

    fun fakeChunk(text: String, score: Float): RelevantChunk {
        val chunk = Chunk(
            id = UUID.randomUUID(),
            runId = runId,
            contentHash = "hash-${text.hashCode()}",
            source = "docs/test.md",
            title = "test.md",
            section = null,
            text = text,
            strategy = ChunkingStrategyType.FIXED_SIZE,
            metadata = emptyMap()
        )
        return RelevantChunk(chunk, score)
    }

    "rerank" - {

        "filters chunks below threshold" {
            runTest {
                // given
                val chunks = listOf(
                    fakeChunk("high", 0.9f),
                    fakeChunk("low", 0.3f),
                    fakeChunk("medium", 0.8f),
                    fakeChunk("very low", 0.1f)
                )
                val config = SearchConfig(threshold = 0.75f, topKFinal = 10)

                // when
                val result = reranker.rerank(chunks, "test query", config)

                // then
                result.rankedChunks shouldHaveSize 2
                result.rankedChunks[0].score shouldBe 0.9f
                result.rankedChunks[1].score shouldBe 0.8f
                result.droppedChunks shouldHaveSize 2
                result.droppedChunks.all { it.reason is DropReason.BelowThreshold } shouldBe true
            }
        }

        "respects topKFinal limit" {
            runTest {
                // given
                val chunks = (1..10).map { fakeChunk("chunk$it", 0.9f) }
                val config = SearchConfig(threshold = 0.5f, topKFinal = 3)

                // when
                val result = reranker.rerank(chunks, "test query", config)

                // then
                result.rankedChunks shouldHaveSize 3
                result.droppedChunks shouldHaveSize 7
                result.droppedChunks.all { it.reason is DropReason.TopKLimit } shouldBe true
            }
        }

        "handles empty chunk list" {
            runTest {
                // given
                val config = SearchConfig()

                // when
                val result = reranker.rerank(emptyList(), "test query", config)

                // then
                result.rankedChunks.shouldBeEmpty()
                result.droppedChunks.shouldBeEmpty()
            }
        }

        "all chunks below threshold returns empty" {
            runTest {
                // given
                val chunks = listOf(fakeChunk("low1", 0.1f), fakeChunk("low2", 0.2f))
                val config = SearchConfig(threshold = 0.75f, topKFinal = 10)

                // when
                val result = reranker.rerank(chunks, "test query", config)

                // then
                result.rankedChunks.shouldBeEmpty()
                result.droppedChunks shouldHaveSize 2
            }
        }

        "tokenUsage is always 0" {
            runTest {
                val chunks = listOf(fakeChunk("chunk", 0.9f))
                val result = reranker.rerank(chunks, "test", SearchConfig())
                result.tokenUsage shouldBe 0
            }
        }

        "isAvailable returns true" {
            runTest {
                reranker.isAvailable() shouldBe true
            }
        }
    }
})
