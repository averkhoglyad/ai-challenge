package io.averkhogliad.ai.challenge.week4.cli.unit.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommand
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandParser
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class RagCommandParserTest : FreeSpec({

    "parse" - {

        // ──── Task 2 commands ────

        ":rag → Toggle" {
            RagCommandParser.parse(":rag") shouldBe RagCommand.Toggle
        }

        ":rag status → Status" {
            RagCommandParser.parse(":rag status") shouldBe RagCommand.Status
        }

        ":rag list → List" {
            RagCommandParser.parse(":rag list") shouldBe RagCommand.List
        }

        ":rag  → Toggle (trailing space)" {
            RagCommandParser.parse(":rag ") shouldBe RagCommand.Toggle
        }

        ":rag unknown → null" {
            RagCommandParser.parse(":rag unknown") shouldBe null
        }

        "random command → null" {
            RagCommandParser.parse(":help") shouldBe null
            RagCommandParser.parse("hello") shouldBe null
        }

        // ──── Task 3: Mode management ────

        ":rag mode reranked" {
            RagCommandParser.parse(":rag mode reranked") shouldBe RagCommand.SetMode(SearchMode.Reranked)
        }

        ":rag mode raw" {
            RagCommandParser.parse(":rag mode raw") shouldBe RagCommand.SetMode(SearchMode.Raw)
        }

        ":rag mode filtered" {
            RagCommandParser.parse(":rag mode filtered") shouldBe RagCommand.SetMode(SearchMode.Filtered)
        }

        ":rag mode rewrite" {
            RagCommandParser.parse(":rag mode rewrite") shouldBe RagCommand.SetMode(SearchMode.Rewrite)
        }

        "rejects invalid mode" {
            RagCommandParser.parse(":rag mode invalid") shouldBe null
        }

        ":rag threshold 0.85" {
            RagCommandParser.parse(":rag threshold 0.85") shouldBe RagCommand.SetThreshold(0.85f)
        }

        "rejects invalid threshold" {
            RagCommandParser.parse(":rag threshold abc") shouldBe null
            RagCommandParser.parse(":rag threshold 2.0") shouldBe null
            RagCommandParser.parse(":rag threshold -0.1") shouldBe null
        }

        ":rag topk 100 10" {
            RagCommandParser.parse(":rag topk 100 10") shouldBe RagCommand.SetTopK(100, 10)
        }

        "rejects invalid topk" {
            RagCommandParser.parse(":rag topk abc") shouldBe null
            RagCommandParser.parse(":rag topk 10 20") shouldBe null // final > initial
            RagCommandParser.parse(":rag topk 0 5") shouldBe null
        }

        ":rag config" {
            RagCommandParser.parse(":rag config") shouldBe RagCommand.Config
        }

        // ──── Task 3: History and analytics ────

        ":rag history" {
            RagCommandParser.parse(":rag history") shouldBe RagCommand.History(limit = 10)
        }

        ":rag history 5" {
            RagCommandParser.parse(":rag history 5") shouldBe RagCommand.History(limit = 5)
        }

        ":rag history --detail 1" {
            RagCommandParser.parse(":rag history --detail 1") shouldBe RagCommand.HistoryDetail(1)
        }

        ":rag history --clear" {
            RagCommandParser.parse(":rag history --clear") shouldBe RagCommand.HistoryClear
        }

        ":rag analyze" {
            RagCommandParser.parse(":rag analyze") shouldBe RagCommand.Analyze
        }

        ":rag analyze --compare raw reranked" {
            RagCommandParser.parse(":rag analyze --compare raw reranked") shouldBe RagCommand.Compare(
                SearchMode.Raw,
                SearchMode.Reranked
            )
        }

        "parses search modes case-insensitively" {
            RagCommandParser.parseSearchMode("RAW") shouldBe SearchMode.Raw
            RagCommandParser.parseSearchMode("Filtered") shouldBe SearchMode.Filtered
            RagCommandParser.parseSearchMode("ReRanked") shouldBe SearchMode.Reranked
            RagCommandParser.parseSearchMode("rewrite") shouldBe SearchMode.Rewrite
        }
    }
})
