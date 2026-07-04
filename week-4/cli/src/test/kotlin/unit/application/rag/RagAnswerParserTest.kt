package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagAnswerParser
import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagResult
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.*

class RagAnswerParserTest : FreeSpec({

    val parser = RagAnswerParser()
    val runId = UUID.randomUUID()

    fun chunk(
        id: String,
        text: String,
        source: String = "doc.md",
        score: Float = 0.9f,
        section: String? = null
    ): RelevantChunk {
        val c = Chunk(
            id = UUID.fromString("00000000-0000-0000-0000-00000000000$id"),
            runId = runId,
            contentHash = "hash-$id",
            source = source,
            title = source,
            section = section,
            text = text,
            strategy = ChunkingStrategyType.FIXED_SIZE,
            metadata = emptyMap()
        )
        return RelevantChunk(c, score)
    }

    "parse" - {

        "returns InsufficientContext when response contains INSUFFICIENT_CONTEXT marker" {
            val response = "INSUFFICIENT_CONTEXT: Please clarify your question"
            val result = parser.parse(response, emptyList())
            result shouldBe RagResult.InsufficientContext("Please clarify your question")
        }

        "extracts null clarificationRequest when no text after INSUFFICIENT_CONTEXT" {
            val response = "INSUFFICIENT_CONTEXT:"
            val result = parser.parse(response, emptyList())
            result shouldBe RagResult.InsufficientContext(null)
        }

        "parses valid JSON with answer and citations_used" {
            val chunks = listOf(
                chunk("1", "Paris is the capital of France", "geography.md", 0.95f)
            )
            val response = "{\"answer\": \"Paris is the capital [1]\", \"citations_used\": [1], \"confidence\": 0.95}"
            val result = parser.parse(response, chunks)
            result is RagResult.Success
            val success = result as RagResult.Success
            success.answer shouldBe "Paris is the capital [1]"
            success.citations shouldHaveSize 1
            success.citations[0].chunkId shouldBe "00000000-0000-0000-0000-000000000001"
            success.citations[0].text shouldBe "Paris is the capital of France"
            success.citations[0].source shouldBe "geography.md"
            success.citations[0].relevanceScore shouldBe 0.95f
        }

        "parses JSON with multiple citations" {
            val chunks = listOf(
                chunk("1", "First fact", "doc1.md", 0.9f),
                chunk("2", "Second fact", "doc2.md", 0.8f),
                chunk("3", "Third fact", "doc3.md", 0.7f)
            )
            val response = "{\"answer\": \"Answer [1][3]\", \"citations_used\": [1, 3], \"confidence\": 0.85}"
            val result = parser.parse(response, chunks) as RagResult.Success
            result.citations shouldHaveSize 2
            result.citations[0].chunkId shouldBe "00000000-0000-0000-0000-000000000001"
            result.citations[1].chunkId shouldBe "00000000-0000-0000-0000-000000000003"
        }

        "falls back to raw text on invalid JSON" {
            val chunks = listOf(chunk("1", "some text"))
            val response = "This is not JSON at all"
            val result = parser.parse(response, chunks)
            result shouldBe RagResult.Fallback("This is not JSON at all")
        }

        "falls back when JSON is malformed" {
            val response = "{\"answer\": \"incomplete\""
            val result = parser.parse(response, emptyList())
            result is RagResult.Fallback
            (result as RagResult.Fallback).rawText shouldBe response
        }

        "returns empty citations when citations_used references out-of-range index" {
            val chunks = listOf(chunk("1", "Only one chunk"))
            val response = "{\"answer\": \"Answer\", \"citations_used\": [5], \"confidence\": 0.5}"
            val result = parser.parse(response, chunks) as RagResult.Success
            result.citations.shouldBeEmpty()
        }

        "handles JSON without answer field" {
            val response = "{\"citations_used\": [1], \"confidence\": 0.8}"
            val result = parser.parse(response, emptyList())
            result is RagResult.Fallback
        }
    }
})
