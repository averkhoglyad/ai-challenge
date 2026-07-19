package io.averkhogliad.ai.challenge.indexer.infrastructure.chunker

import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategyType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class FixedSizeChunkerTest : FreeSpec({

    "FixedSizeChunker" - {

        "returns single chunk for text smaller than chunkSize" {
            val chunker = FixedSizeChunker(chunkSize = 100)
            val doc = Document(Path.of("test.txt"), "short text")

            val chunks = chunker.chunk(doc)

            chunks shouldHaveSize 1
            chunks[0].text shouldBe "short text"
        }

        "splits long text into multiple chunks" {
            val chunker = FixedSizeChunker(chunkSize = 10, overlap = 2)
            val doc = Document(Path.of("test.txt"), "a".repeat(25))

            val chunks = chunker.chunk(doc)

            chunks.size shouldBe 3 // 10 + 10 + 5 with overlap 2
        }

        "has correct type" {
            val chunker = FixedSizeChunker()
            chunker.type shouldBe ChunkingStrategyType.FIXED_SIZE
        }
    }
})
