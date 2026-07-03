package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagQueryProcessor
import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Embedding
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RagPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.VectorSearchPort
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.*

class RagQueryProcessorTest : FreeSpec({

    val config = TaskExecutionConfig(temperature = 0.7, maxTokens = 500, modelId = ModelId("test-model"))
    val runId = UUID.randomUUID()

    lateinit var embeddingGenerator: EmbeddingGenerator
    lateinit var vectorSearchPort: VectorSearchPort
    lateinit var promptBuilder: RagPromptBuilder
    lateinit var llmPort: LlmPort
    lateinit var indexRepository: IndexRepository
    lateinit var processor: RagQueryProcessor

    fun fakeChunk(text: String): Chunk = Chunk(
        id = UUID.randomUUID(),
        runId = runId,
        contentHash = "hash-${text.hashCode()}",
        source = "docs/test.md",
        title = "test.md",
        section = null,
        text = text,
        strategy = ChunkingStrategyType.STRUCTURAL,
        metadata = emptyMap()
    )

    beforeEach {
        embeddingGenerator = mockk()
        vectorSearchPort = mockk()
        promptBuilder = mockk()
        llmPort = mockk()
        indexRepository = mockk()
        processor = RagQueryProcessor(
            embeddingGenerator = embeddingGenerator,
            vectorSearchPort = vectorSearchPort,
            promptBuilder = promptBuilder,
            llmPort = llmPort,
            indexRepository = indexRepository
        )
    }

    "process" - {

        "RAG disabled → plain LLM, no sources" {
            runTest {
                // given
                val ragState = RagSessionState(enabled = false)
                coEvery { llmPort.chat(Prompt("question?"), config) } returns TaskResult.Success("plain answer")

                // when
                val result = processor.process("question?", ragState, config)

                // then
                result.answer shouldBe "plain answer"
                result.ragEnabled shouldBe false
                result.fallbackToPlain shouldBe false
                result.sources.shouldBeEmpty()
            }
        }

        "RAG enabled but no active index → fallback" {
            runTest {
                // given
                val ragState = RagSessionState(enabled = true)
                coEvery { indexRepository.getActiveIndex() } returns null
                coEvery { llmPort.chat(Prompt("question?"), config) } returns TaskResult.Success("plain answer")

                // when
                val result = processor.process("question?", ragState, config)

                // then
                result.answer shouldBe "plain answer"
                result.ragEnabled shouldBe true
                result.fallbackToPlain shouldBe true
            }
        }

        "RAG enabled + active index + chunks found → full RAG with sources" {
            runTest {
                // given
                val ragState = RagSessionState(enabled = true)
                val chunk1 = fakeChunk("chunk text 1")
                val chunk2 = fakeChunk("chunk text 2")
                val relevantChunks = listOf(
                    RelevantChunk(chunk1, 0.9f),
                    RelevantChunk(chunk2, 0.8f)
                )
                val embeddingVector = floatArrayOf(0.1f, 0.2f)

                coEvery { indexRepository.getActiveIndex() } returns runId
                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery {
                    vectorSearchPort.search(embeddingVector, runId, ragState.topK, ragState.similarityThreshold)
                } returns relevantChunks
                coEvery { promptBuilder.build("question?", relevantChunks) } returns "augmented prompt"
                coEvery { llmPort.chat(Prompt("augmented prompt"), config) } returns TaskResult.Success("rag answer")

                // when
                val result = processor.process("question?", ragState, config)

                // then
                result.answer shouldBe "rag answer"
                result.ragEnabled shouldBe true
                result.fallbackToPlain shouldBe false
                result.sources shouldHaveSize 2
                result.sources[0].score shouldBe 0.9f
                result.sources[1].score shouldBe 0.8f
            }
        }

        "RAG enabled + active index + empty search → fallback" {
            runTest {
                // given
                val ragState = RagSessionState(enabled = true)
                val embeddingVector = floatArrayOf(0.1f, 0.2f)

                coEvery { indexRepository.getActiveIndex() } returns runId
                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery {
                    vectorSearchPort.search(embeddingVector, runId, ragState.topK, ragState.similarityThreshold)
                } returns emptyList()
                coEvery { llmPort.chat(Prompt("question?"), config) } returns TaskResult.Success("plain answer")

                // when
                val result = processor.process("question?", ragState, config)

                // then
                result.answer shouldBe "plain answer"
                result.ragEnabled shouldBe true
                result.fallbackToPlain shouldBe true
                result.sources.shouldBeEmpty()
            }
        }

        "embedding error → graceful fallback" {
            runTest {
                // given
                val ragState = RagSessionState(enabled = true)
                coEvery { indexRepository.getActiveIndex() } returns runId
                coEvery { embeddingGenerator.generateBatch(any()) } throws RuntimeException("Connection timeout")
                coEvery { llmPort.chat(Prompt("question?"), config) } returns TaskResult.Success("plain answer")

                // when
                val result = processor.process("question?", ragState, config)

                // then
                result.answer shouldBe "plain answer"
                result.fallbackToPlain shouldBe true
            }
        }

        "vector search error → graceful fallback" {
            runTest {
                // given
                val ragState = RagSessionState(enabled = true)
                val embeddingVector = floatArrayOf(0.1f, 0.2f)

                coEvery { indexRepository.getActiveIndex() } returns runId
                coEvery { embeddingGenerator.generateBatch(any()) } returns listOf(
                    Embedding(UUID.randomUUID(), embeddingVector, "test-model")
                )
                coEvery {
                    vectorSearchPort.search(any(), any(), any(), any())
                } throws RuntimeException("DB error")
                coEvery { llmPort.chat(Prompt("question?"), config) } returns TaskResult.Success("plain answer")

                // when
                val result = processor.process("question?", ragState, config)

                // then
                result.answer shouldBe "plain answer"
                result.fallbackToPlain shouldBe true
            }
        }
    }
})
