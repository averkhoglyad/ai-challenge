package io.averkhogliad.ai.challenge.week4.cli.it.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.SearchPipeline
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Embedding
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.QueryRewriter
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RewriteResult
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.VectorSearchPort
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rerank.ThresholdReranker
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.util.*

/**
 * Интеграционные тесты для [SearchPipeline] с реальным [ThresholdReranker].
 *
 * Внешние зависимости (EmbeddingGenerator, VectorSearchPort, QueryRewriter)
 * замоканы для изоляции тестируемого pipeline.
 */
class SearchPipelineIT : FreeSpec({

    val runId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val embeddingVector = FloatArray(384) { 0.5f }

    fun fakeChunk(text: String, idx: Int = 0): Chunk = Chunk(
        id = UUID.randomUUID(),
        runId = runId,
        contentHash = "hash-$idx",
        source = "test-$idx.txt",
        title = "Test $idx",
        section = null,
        text = text,
        strategy = ChunkingStrategyType.FIXED_SIZE,
        metadata = emptyMap()
    )

    fun fakeRelevantChunk(chunk: Chunk, score: Float): RelevantChunk = RelevantChunk(chunk, score)

    fun fakeEmbedding(chunkId: UUID): Embedding = Embedding(
        chunkId = chunkId,
        vector = embeddingVector,
        model = "test-model"
    )

    fun createEmbeddingGenerator(): EmbeddingGenerator = object : EmbeddingGenerator {
        override suspend fun healthCheck(): Boolean = true
        override suspend fun generateBatch(
            entries: List<Pair<UUID, String>>
        ): List<Embedding> = entries.map { fakeEmbedding(it.first) }
    }

    fun createQueryRewriter(): QueryRewriter = object : QueryRewriter {
        override suspend fun rewrite(query: String): RewriteResult =
            RewriteResult(rewrittenQuery = query, tokenUsage = 0)
    }

    fun createVectorSearch(vararg scoredChunks: Pair<Chunk, Float>): VectorSearchPort =
        object : VectorSearchPort {
            override suspend fun search(
                queryEmbedding: FloatArray,
                runId: UUID,
                topK: Int,
                threshold: Float
            ): List<RelevantChunk> = scoredChunks.map { (c, s) -> fakeRelevantChunk(c, s) }.take(topK)
        }

    "Filtered mode with real ThresholdReranker" - {

        "should filter chunks by threshold and topK" {
            runTest {
                val chunks = listOf(
                    fakeChunk("highly relevant", 0),
                    fakeChunk("relevant", 1),
                    fakeChunk("borderline", 2),
                    fakeChunk("low score", 3),
                    fakeChunk("irrelevant", 4)
                )

                val pipeline = SearchPipeline(
                    queryRewriter = createQueryRewriter(),
                    vectorSearch = createVectorSearch(
                        chunks[0] to 0.95f,
                        chunks[1] to 0.82f,
                        chunks[2] to 0.76f,
                        chunks[3] to 0.60f,
                        chunks[4] to 0.30f
                    ),
                    reranker = ThresholdReranker(),
                    embeddingGenerator = createEmbeddingGenerator()
                )

                val config = SearchConfig(
                    mode = SearchMode.Filtered,
                    topKInitial = 5,
                    topKFinal = 2,
                    threshold = 0.75f
                )

                val result = pipeline.execute("test query", config, runId)

                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()

                // threshold ≥ 0.75 keeps [0.95, 0.82, 0.76] = 3
                // topKFinal = 2 keeps first 2
                ctx.filteredResults shouldHaveSize 2
                ctx.filteredResults[0].score shouldBe 0.95f
                ctx.filteredResults[1].score shouldBe 0.82f

                ctx.droppedChunks shouldHaveSize 3 // 2 below threshold + 1 by topK
                ctx.stats.chunks.initial shouldBe 5
                ctx.stats.chunks.final shouldBe 2
                ctx.stats.dropped.byThreshold shouldBe 2
                ctx.stats.dropped.byTopK shouldBe 1
            }
        }

        "should return empty when all chunks below threshold" {
            runTest {
                val chunks = listOf(
                    fakeChunk("low-1", 0),
                    fakeChunk("low-2", 1)
                )

                val pipeline = SearchPipeline(
                    queryRewriter = createQueryRewriter(),
                    vectorSearch = createVectorSearch(
                        chunks[0] to 0.40f,
                        chunks[1] to 0.20f
                    ),
                    reranker = ThresholdReranker(),
                    embeddingGenerator = createEmbeddingGenerator()
                )

                val config =
                    SearchConfig(mode = SearchMode.Filtered, topKInitial = 10, topKFinal = 3, threshold = 0.75f)
                val result = pipeline.execute("test", config, runId)

                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()
                ctx.filteredResults shouldHaveSize 0
                ctx.droppedChunks shouldHaveSize 2
                ctx.stats.dropped.byThreshold shouldBe 2
            }
        }
    }

    "Raw mode" - {

        "should take topKFinal without filtering" {
            runTest {
                val chunks = (0..4).map { fakeChunk("c$it", it) }
                val scores = listOf(0.90f, 0.80f, 0.70f, 0.10f, 0.05f)

                val pipeline = SearchPipeline(
                    queryRewriter = createQueryRewriter(),
                    vectorSearch = createVectorSearch(*chunks.zip(scores).toTypedArray()),
                    reranker = ThresholdReranker(),
                    embeddingGenerator = createEmbeddingGenerator()
                )

                val config = SearchConfig(mode = SearchMode.Raw, topKInitial = 5, topKFinal = 3)
                val result = pipeline.execute("test", config, runId)

                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()
                ctx.filteredResults shouldHaveSize 3
                ctx.droppedChunks shouldHaveSize 0
            }
        }
    }

    "Metrics" - {

        "should populate all 5 metrics correctly" {
            runTest {
                val chunks = listOf(
                    fakeChunk("relevant", 0),
                    fakeChunk("medium", 1),
                    fakeChunk("low", 2)
                )

                val pipeline = SearchPipeline(
                    queryRewriter = createQueryRewriter(),
                    vectorSearch = createVectorSearch(
                        chunks[0] to 0.90f,
                        chunks[1] to 0.70f,
                        chunks[2] to 0.50f
                    ),
                    reranker = ThresholdReranker(),
                    embeddingGenerator = createEmbeddingGenerator()
                )

                val config =
                    SearchConfig(mode = SearchMode.Filtered, topKInitial = 10, topKFinal = 2, threshold = 0.75f)
                val result = pipeline.execute("metrics test", config, runId)

                result.isSuccess shouldBe true
                val ctx = result.getOrThrow()

                (ctx.stats.totalMs >= 0L) shouldBe true
                ctx.stats.chunks.initial shouldBe 3
                ctx.stats.chunks.final shouldBe 1  // only 0.90 ≥ 0.75
                ctx.stats.score.initialAvg shouldBe 0.7f
                ctx.stats.tokens.rewrite shouldBe null
                ctx.stats.tokens.rerank shouldBe null
                ctx.stats.tokens.answer shouldBe 0
                ctx.stats.dropped.byThreshold shouldBe 2 // 0.70 and 0.50 below 0.75
                ctx.stats.dropped.byTopK shouldBe 0
            }
        }
    }
})
