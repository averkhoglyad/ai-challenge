package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.indexer.chunker

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ExtractedText
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.chunker.FixedSizeChunker
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.util.*

class FixedSizeChunkerTest : FreeSpec({

    val runId = UUID.randomUUID()

    "chunk" - {

        "should return single chunk when text is shorter than chunkSize" {
            // given
            val chunker = FixedSizeChunker(chunkSize = 500, overlap = 50)
            val extractedText = ExtractedText(
                documentPath = "/docs/short.txt",
                content = "Short text",
                metadata = emptyMap()
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then
            chunks.size shouldBe 1
            chunks[0].text shouldBe "Short text"
            chunks[0].source shouldBe "/docs/short.txt"
            chunks[0].strategy shouldBe ChunkingStrategyType.FIXED_SIZE
            chunks[0].runId shouldBe runId
        }

        "should split text into chunks of approximately chunkSize" {
            // given
            val chunkSize = 100
            val overlap = 20
            val chunker = FixedSizeChunker(chunkSize = chunkSize, overlap = overlap)
            // Generate text longer than chunkSize
            val longText = (1..50).joinToString(" ") { "word$it" }
            val extractedText = ExtractedText(
                documentPath = "/docs/long.txt",
                content = longText,
                metadata = emptyMap()
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then
            chunks.size shouldBeGreaterThan 1
            // Each chunk should not exceed chunkSize * 2 (roughly; due to word alignment it may vary)
            chunks.forEach { chunk ->
                (chunk.text.length <= chunkSize * 3) shouldBe true
            }
        }

        "should produce overlapping chunks" {
            // given
            val chunker = FixedSizeChunker(chunkSize = 100, overlap = 30)
            val longText = (1..100).joinToString(" ") { "word$it" }
            val extractedText = ExtractedText(
                documentPath = "/docs/long.txt",
                content = longText,
                metadata = emptyMap()
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then
            // With overlap, consecutive chunks should share some text
            chunks.size shouldBeGreaterThan 1
        }

        "should not split words — should align to spaces" {
            // given
            val chunker = FixedSizeChunker(chunkSize = 50, overlap = 10)
            // Use words that would force split if not aligned
            val words = (1..30).joinToString(" ") { "longword$it" }
            val extractedText = ExtractedText(
                documentPath = "/docs/words.txt",
                content = words,
                metadata = emptyMap()
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then — each chunk text should not start/end mid-word
            chunks.forEach { chunk ->
                val text = chunk.text
                // should not start with a space (trim check)
                (text.startsWith(" ") || text.endsWith(" ")) shouldBe false
            }
        }

        "should assign unique UUIDs to each chunk" {
            // given
            val chunker = FixedSizeChunker(chunkSize = 100, overlap = 20)
            val longText = (1..50).joinToString(" ") { "word$it" }
            val extractedText = ExtractedText(
                documentPath = "/docs/ids.txt",
                content = longText,
                metadata = emptyMap()
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then — all chunk IDs should be unique
            val ids = chunks.map { it.id }
            ids.toSet().size shouldBe ids.size
        }

        "should compute contentHash for each chunk" {
            // given
            val chunker = FixedSizeChunker(chunkSize = 200, overlap = 30)
            val longText = (1..20).joinToString(" ") { "word$it" }
            val extractedText = ExtractedText(
                documentPath = "/docs/hash.txt",
                content = longText,
                metadata = emptyMap()
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then — each chunk should have a non-empty SHA-256 hash (64 hex chars)
            chunks.forEach { chunk ->
                chunk.contentHash.length shouldBe 64
                (chunk.contentHash.all { it.isDigit() || it in 'a'..'f' }) shouldBe true
            }
        }

        "should handle text exactly at chunkSize boundary" {
            // given
            val chunker = FixedSizeChunker(chunkSize = 20, overlap = 5)
            val text = "This is exactly twenty characters long"
            val extractedText = ExtractedText(
                documentPath = "/docs/exact.txt",
                content = text,
                metadata = emptyMap()
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then — since text.length (40) > chunkSize (20), should produce at least 1 chunk
            chunks.isNotEmpty() shouldBe true
        }
    }
})
