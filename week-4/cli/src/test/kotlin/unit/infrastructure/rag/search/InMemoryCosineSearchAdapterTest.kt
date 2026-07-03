package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.rag.search

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Embedding
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexedChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.search.InMemoryCosineSearchAdapter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.util.*

class InMemoryCosineSearchAdapterTest : FreeSpec({

    val runId = UUID.randomUUID()
    lateinit var repository: IndexRepository
    lateinit var adapter: InMemoryCosineSearchAdapter

    fun indexedChunk(text: String, vector: FloatArray): IndexedChunk {
        val chunk = Chunk(
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
        val embedding = Embedding(
            chunkId = chunk.id,
            vector = vector,
            model = "test-model"
        )
        return IndexedChunk(chunk, embedding)
    }

    beforeEach {
        repository = mockk()
        adapter = InMemoryCosineSearchAdapter(repository)
    }

    "search" - {

        "returns top-K chunks sorted by score desc" {
            // given — 3 chunks with different vectors
            val queryVector = floatArrayOf(1f, 0f, 0f)
            val chunks = listOf(
                indexedChunk("most relevant", floatArrayOf(1f, 0.1f, 0f)),    // ~1.0
                indexedChunk("medium relevant", floatArrayOf(0.5f, 0.5f, 0f)), // ~0.7
                indexedChunk("least relevant", floatArrayOf(0f, 1f, 0f))       // ~0.0
            )
            coEvery { repository.getChunksByRunId(runId) } returns chunks

            // when
            val result = adapter.search(queryVector, runId, topK = 2, threshold = 0.0f)

            // then
            result shouldHaveSize 2
            result[0].chunk.text shouldBe "most relevant"
            result[1].chunk.text shouldBe "medium relevant"
            result[0].score shouldBeGreaterThan result[1].score
        }

        "filters chunks below threshold" {
            // given
            val queryVector = floatArrayOf(1f, 0f, 0f)
            val chunks = listOf(
                indexedChunk("high score", floatArrayOf(1f, 0f, 0f)),    // ~1.0
                indexedChunk("low score", floatArrayOf(0f, 1f, 0f))       // ~0.0
            )
            coEvery { repository.getChunksByRunId(runId) } returns chunks

            // when — threshold = 0.5
            val result = adapter.search(queryVector, runId, topK = 10, threshold = 0.5f)

            // then
            result shouldHaveSize 1
            result[0].chunk.text shouldBe "high score"
        }

        "returns empty list for empty run" {
            // given
            coEvery { repository.getChunksByRunId(runId) } returns emptyList()

            // when
            val result = adapter.search(floatArrayOf(1f, 0f), runId, topK = 5, threshold = 0.0f)

            // then
            result.shouldBeEmpty()
        }

        "honors topK limit" {
            // given — 5 chunks, all above threshold
            val queryVector = floatArrayOf(1f, 0f)
            val chunks = (1..5).map {
                indexedChunk("chunk $it", floatArrayOf(1f, 0f))
            }
            coEvery { repository.getChunksByRunId(runId) } returns chunks

            // when
            val result = adapter.search(queryVector, runId, topK = 3, threshold = 0.0f)

            // then
            result shouldHaveSize 3
        }
    }
})
