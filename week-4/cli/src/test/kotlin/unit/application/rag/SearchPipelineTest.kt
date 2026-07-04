package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.SearchPipeline
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Embedding
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.*

class SearchPipelineTest : FreeSpec({

    val runId = UUID.randomUUID()

    lateinit var queryRewriter: QueryRewriter
    lateinit var vectorSearch: VectorSearchPort
    lateinit var reranker: RerankingStrategy
    lateinit var embeddingGenerator: EmbeddingGenerator
    lateinit var pipeline: SearchPipeline

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

    val embeddingVector = floatArrayOf(0.1f, 0.2f, 0.3f)

    beforeEach {
        queryRewriter = mockk()
        vectorSearch = mockk()
        reranker = mockk()
        embeddingGenerator = mockk()
        pipeline = SearchPipeline(queryRewriter, vectorSearch, reranker, embeddingGenerator)
    }

    "Raw mode" - {

        "returns topKFinal results without filtering" {
            runTest {
                // given
                val chunks = (1..10).map { fakeChunk("chunk$it", (1.0f - it * 0.05f)) }
                val config = SearchConfig(mode = SearchMode.Raw, topKInitial = 10, topKFinal = 3)

                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery { vectorSearch.search(embeddingVector, runId, 10, 0.0f) } returns chunks

                // when
                val result = pipeline.execute("test query", config, runId)

                // then
                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()
                ctx.filteredResults shouldHaveSize 3
                ctx.filteredResults[0].chunk.text shouldBe "chunk1" // highest score
                ctx.stats.mode shouldBe SearchMode.Raw
            }
        }
    }

    "Filtered mode" - {

        "applies threshold reranker" {
            runTest {
                // given
                val chunks = listOf(fakeChunk("high", 0.9f), fakeChunk("low", 0.3f))
                val config = SearchConfig(mode = SearchMode.Filtered, topKInitial = 10, topKFinal = 5)

                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery { vectorSearch.search(embeddingVector, runId, 10, 0.0f) } returns chunks
                coEvery { reranker.rerank(chunks, "test query", config) } returns RerankResult(
                    rankedChunks = listOf(chunks[0]),
                    droppedChunks = listOf(DroppedChunk(chunks[1], DropReason.BelowThreshold(0.75f))),
                    tokenUsage = 0
                )

                // when
                val result = pipeline.execute("test query", config, runId)

                // then
                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()
                ctx.filteredResults shouldHaveSize 1
                ctx.stats.mode shouldBe SearchMode.Filtered
            }
        }
    }

    "Reranked mode" - {

        "applies reranker" {
            runTest {
                // given
                val chunks = listOf(fakeChunk("chunkA", 0.5f), fakeChunk("chunkB", 0.5f))
                val config = SearchConfig(mode = SearchMode.Reranked, topKInitial = 10, topKFinal = 2)

                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery { vectorSearch.search(embeddingVector, runId, 10, 0.0f) } returns chunks
                coEvery { reranker.rerank(chunks, "test query", config) } returns RerankResult(
                    rankedChunks = listOf(chunks[0]),
                    droppedChunks = listOf(DroppedChunk(chunks[1], DropReason.LowRerankScore(0.3f, 0.0f))),
                    tokenUsage = 700
                )

                // when
                val result = pipeline.execute("test query", config, runId)

                // then
                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()
                ctx.filteredResults shouldHaveSize 1
                ctx.stats.mode shouldBe SearchMode.Reranked
                ctx.stats.tokens.rerank shouldBe 700
            }
        }
    }

    "Rewrite mode" - {

        "calls queryRewriter then reranker" {
            runTest {
                // given
                val chunks = listOf(fakeChunk("relevant content", 0.7f))
                val config = SearchConfig(mode = SearchMode.Rewrite, topKInitial = 10, topKFinal = 3)

                coEvery { queryRewriter.rewrite("original query") } returns RewriteResult(
                    rewrittenQuery = "rewritten query with keywords",
                    tokenUsage = 150
                )
                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery { vectorSearch.search(embeddingVector, runId, 10, 0.0f) } returns chunks
                coEvery { reranker.rerank(chunks, "rewritten query with keywords", config) } returns RerankResult(
                    rankedChunks = chunks,
                    droppedChunks = emptyList(),
                    tokenUsage = 500
                )

                // when
                val result = pipeline.execute("original query", config, runId)

                // then
                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()
                ctx.rewrittenQuery shouldBe "rewritten query with keywords"
                ctx.stats.tokens.rewrite shouldBe 150
                ctx.stats.tokens.rerank shouldBe 500
            }
        }
    }

    "metrics collection" - {

        "all 5 metrics populated" {
            runTest {
                // given
                val chunks = listOf(
                    fakeChunk("chunk1", 0.9f),
                    fakeChunk("chunk2", 0.7f),
                    fakeChunk("chunk3", 0.5f)
                )
                val config = SearchConfig(mode = SearchMode.Filtered, topKInitial = 10, topKFinal = 2)

                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery { vectorSearch.search(embeddingVector, runId, 10, 0.0f) } returns chunks
                coEvery { reranker.rerank(chunks, "test query", config) } returns RerankResult(
                    rankedChunks = listOf(chunks[0], chunks[1]),
                    droppedChunks = listOf(DroppedChunk(chunks[2], DropReason.BelowThreshold(0.75f))),
                    tokenUsage = 0
                )

                // when
                val result = pipeline.execute("test query", config, runId)

                // then
                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()
                (ctx.stats.totalMs >= 0L) shouldBe true // will be very quick in test
                ctx.stats.chunks.initial shouldBe 3
                ctx.stats.chunks.filtered shouldBe 3
                ctx.stats.chunks.final shouldBe 2
                ctx.stats.score.initialAvg shouldBe 0.7f
                ctx.stats.dropped.byThreshold shouldBe 1
            }
        }
    }

    "graceful degradation" - {

        "rewrite fallback to original query on error" {
            runTest {
                // given
                val chunks = listOf(fakeChunk("content", 0.8f))
                val config = SearchConfig(mode = SearchMode.Rewrite, topKInitial = 10, topKFinal = 3)

                coEvery { queryRewriter.rewrite("original query") } throws RuntimeException("Rewrite service down")
                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery { vectorSearch.search(embeddingVector, runId, 10, 0.0f) } returns chunks
                coEvery { reranker.rerank(chunks, "original query", config) } returns RerankResult(
                    rankedChunks = chunks,
                    droppedChunks = emptyList(),
                    tokenUsage = 0
                )

                // when
                val result = pipeline.execute("original query", config, runId)

                // then — search continues with original query, rewriteTokens = 0
                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()
                ctx.rewrittenQuery shouldBe null
                ctx.stats.tokens.rewrite shouldBe 0
            }
        }
    }

    "handles embedding failure" {
        runTest {
            // given
            val config = SearchConfig(mode = SearchMode.Raw)
            coEvery { embeddingGenerator.generateBatch(any()) } throws RuntimeException("Embedding API down")

            // when
            val result = pipeline.execute("test query", config, runId)

            // then
            result.isFailure shouldBe true
        }
    }
})
