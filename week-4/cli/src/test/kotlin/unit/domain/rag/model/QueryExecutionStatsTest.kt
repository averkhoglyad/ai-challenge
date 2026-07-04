package io.averkhogliad.ai.challenge.week4.cli.unit.domain.rag.model

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.*

class QueryExecutionStatsTest : FreeSpec({

    "TokenBreakdown" - {

        "total computes correctly with rewrite and rerank" {
            val tb = TokenBreakdown(rewrite = 200, rerank = 800, answer = 450)
            tb.total shouldBe 1450
        }

        "total computes correctly with only answer" {
            val tb = TokenBreakdown(rewrite = null, rerank = null, answer = 450)
            tb.total shouldBe 450
        }

        "total computes correctly with null tokens as 0" {
            val tb = TokenBreakdown(rewrite = null, rerank = 500, answer = 300)
            tb.total shouldBe 800
        }
    }

    "ChunkFlow" - {

        "reflects pipeline stages" {
            val flow = ChunkFlow(initial = 100, filtered = 30, final = 5)
            flow.initial shouldBe 100
            flow.filtered shouldBe 30
            flow.final shouldBe 5
        }
    }

    "ScoreDelta" - {

        "computes average difference correctly" {
            val delta = ScoreDelta(initialAvg = 0.65f, filteredAvg = 0.89f)
            delta.initialAvg shouldBe 0.65f
            delta.filteredAvg shouldBe 0.89f
        }
    }

    "DropBreakdown" - {

        "counts drops by reason" {
            val breakdown = DropBreakdown(byThreshold = 88, byTopK = 7, byRerank = 0)
            breakdown.byThreshold shouldBe 88
            breakdown.byTopK shouldBe 7
            breakdown.byRerank shouldBe 0
        }
    }

    "QueryExecutionStats" - {

        "holds all 5 metrics correctly" {
            val stats = QueryExecutionStats(
                queryId = UUID.randomUUID(),
                timestamp = Instant.now(),
                mode = SearchMode.Reranked,
                totalMs = 3200,
                chunks = ChunkFlow(100, 12, 5),
                score = ScoreDelta(0.65f, 0.88f),
                tokens = TokenBreakdown(rewrite = null, rerank = 800, answer = 450),
                dropped = DropBreakdown(byThreshold = 88, byTopK = 7, byRerank = 0)
            )
            stats.totalMs shouldBe 3200
            stats.mode shouldBe SearchMode.Reranked
            stats.chunks.final shouldBe 5
            stats.score.filteredAvg shouldBe 0.88f
            stats.tokens.total shouldBe 1250
            stats.dropped.byThreshold shouldBe 88
        }
    }
})
