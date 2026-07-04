package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.RelevanceChecker
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevanceCheckResult
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.util.*

class RelevanceCheckerTest : FreeSpec({

    val checker = RelevanceChecker()
    val runId = UUID.randomUUID()

    fun chunk(id: String, score: Float): RelevantChunk {
        val c = Chunk(
            id = UUID.randomUUID(),
            runId = runId,
            contentHash = "hash-$id",
            source = "doc.md",
            title = "doc",
            section = null,
            text = "text $id",
            strategy = ChunkingStrategyType.FIXED_SIZE,
            metadata = emptyMap()
        )
        return RelevantChunk(c, score)
    }

    "check" - {

        "returns Sufficient when maxScore >= threshold" {
            val chunks = listOf(chunk("1", 0.85f), chunk("2", 0.70f))
            val result = checker.check(chunks, 0.80f)
            result shouldBe RelevanceCheckResult.Sufficient(
                chunks = chunks,
                maxScore = 0.85f,
                averageScore = 0.775f
            )
        }

        "returns Sufficient when maxScore equals threshold" {
            val chunks = listOf(chunk("1", 0.80f))
            val result = checker.check(chunks, 0.80f)
            result is RelevanceCheckResult.Sufficient
            (result as RelevanceCheckResult.Sufficient).maxScore shouldBe 0.80f
        }

        "returns Insufficient when maxScore < threshold" {
            val chunks = listOf(chunk("1", 0.65f))
            val result = checker.check(chunks, 0.80f)
            result shouldBe RelevanceCheckResult.Insufficient(
                maxScore = 0.65f,
                threshold = 0.80f,
                chunks = chunks
            )
        }

        "returns Insufficient for empty list with maxScore=0" {
            val result = checker.check(emptyList(), 0.80f)
            result shouldBe RelevanceCheckResult.Insufficient(
                maxScore = 0f,
                threshold = 0.80f,
                chunks = emptyList()
            )
        }

        "calculates averageScore correctly" {
            val chunks = listOf(chunk("1", 0.9f), chunk("2", 0.7f), chunk("3", 0.5f))
            val result = checker.check(chunks, 0.6f) as RelevanceCheckResult.Sufficient
            result.averageScore shouldBe 0.7f
        }

        "maxScore is from highest scoring chunk" {
            val chunks = listOf(chunk("low", 0.3f), chunk("mid", 0.5f), chunk("high", 0.95f))
            val result = checker.check(chunks, 0.90f) as RelevanceCheckResult.Sufficient
            result.maxScore shouldBe 0.95f
            result.chunks shouldBe chunks
        }
    }
})
