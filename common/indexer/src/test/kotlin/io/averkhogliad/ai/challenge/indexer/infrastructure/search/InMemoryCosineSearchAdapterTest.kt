package io.averkhogliad.ai.challenge.indexer.infrastructure.search

import io.averkhogliad.ai.challenge.indexer.domain.model.Chunk
import io.averkhogliad.ai.challenge.indexer.domain.model.Embedding
import io.averkhogliad.ai.challenge.indexer.domain.model.IndexedChunk
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.*

class InMemoryCosineSearchAdapterTest : FreeSpec({

    "InMemoryCosineSearchAdapter" - {

        "returns empty list when index is empty" {
            val adapter = InMemoryCosineSearchAdapter()
            val results = adapter.search(FloatArray(3) { 1f })
            results shouldHaveSize 0
        }

        "finds similar vectors" {
            val adapter = InMemoryCosineSearchAdapter()
            val chunkId1 = UUID.randomUUID()
            val chunkId2 = UUID.randomUUID()
            val chunk1 = IndexedChunk(
                Chunk(chunkId1, "hello", "src"),
                Embedding(chunkId1, floatArrayOf(1f, 0f, 0f), "test-model"),
            )
            val chunk2 = IndexedChunk(
                Chunk(chunkId2, "world", "src"),
                Embedding(chunkId2, floatArrayOf(0f, 1f, 0f), "test-model"),
            )
            adapter.addEmbedding(chunk1)
            adapter.addEmbedding(chunk2)

            val results = adapter.search(floatArrayOf(1f, 0f, 0f), topK = 1)

            results shouldHaveSize 1
            results[0].chunk.text shouldBe "hello"
        }

        "clear removes all embeddings" {
            val adapter = InMemoryCosineSearchAdapter()
            val chunkId = UUID.randomUUID()
            val chunk = IndexedChunk(
                Chunk(chunkId, "text", "src"),
                Embedding(chunkId, floatArrayOf(1f), "model"),
            )
            adapter.addEmbedding(chunk)
            adapter.clear()

            val results = adapter.search(floatArrayOf(1f))
            results shouldHaveSize 0
        }
    }
})
