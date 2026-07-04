package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.rag.rerank

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rerank.LlmReranker
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rerank.ThresholdReranker
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.*

class LlmRerankerTest : FreeSpec({

    val runId = UUID.randomUUID()

    lateinit var llmPort: LlmPort
    lateinit var reranker: LlmReranker

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

    beforeEach {
        llmPort = mockk()
        val thresholdReranker = ThresholdReranker()
        reranker = LlmReranker(llmPort, thresholdReranker)
    }

    "rerank" - {

        "parses valid JSON scores correctly" {
            runTest {
                // given
                val chunks = listOf(
                    fakeChunk("chunk A about dogs", 0.5f),
                    fakeChunk("chunk B about cats", 0.5f),
                    fakeChunk("chunk C about fish", 0.5f)
                )
                val llmResponse = """{"scores": [9, 3, 7]}"""
                coEvery { llmPort.chat(any<Prompt>(), any()) } returns TaskResult.Success(llmResponse)

                val config = SearchConfig(topKFinal = 2)

                // when
                val result = reranker.rerank(chunks, "tell me about dogs", config)

                // then
                result.rankedChunks shouldHaveSize 2
                // Best should be chunk A (score=9/10=0.9)
                result.rankedChunks[0].chunk.text shouldBe "chunk A about dogs"
                result.droppedChunks shouldHaveSize 1
            }
        }

        "falls back to threshold on LLM error" {
            runTest {
                // given
                val chunks = listOf(
                    fakeChunk("high", 0.9f),
                    fakeChunk("low", 0.3f)
                )
                coEvery { llmPort.chat(any<Prompt>(), any()) } throws RuntimeException("Connection refused")

                val config = SearchConfig(threshold = 0.75f, topKFinal = 10)

                // when
                val result = reranker.rerank(chunks, "test query", config)

                // then — threshold fallback: only the 0.9f chunk survives
                result.rankedChunks shouldHaveSize 1
                result.rankedChunks[0].score shouldBe 0.9f
            }
        }

        "falls back to threshold on invalid JSON" {
            runTest {
                // given
                val chunks = listOf(
                    fakeChunk("high", 0.9f),
                    fakeChunk("low", 0.3f)
                )
                coEvery { llmPort.chat(any<Prompt>(), any()) } returns TaskResult.Success("not a json at all")

                val config = SearchConfig(threshold = 0.75f, topKFinal = 10)

                // when
                val result = reranker.rerank(chunks, "test query", config)

                // then — threshold fallback
                result.rankedChunks shouldHaveSize 1
                result.rankedChunks[0].score shouldBe 0.9f
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

        "respects topKFinal limit" {
            runTest {
                // given
                val chunks = (1..5).map { fakeChunk("chunk$it", 0.5f) }
                val llmResponse = """{"scores": [10, 9, 8, 7, 6]}"""
                coEvery { llmPort.chat(any<Prompt>(), any()) } returns TaskResult.Success(llmResponse)

                val config = SearchConfig(topKFinal = 2)

                // when
                val result = reranker.rerank(chunks, "test", config)

                // then
                result.rankedChunks shouldHaveSize 2
                result.droppedChunks shouldHaveSize 3
            }
        }
    }
})
