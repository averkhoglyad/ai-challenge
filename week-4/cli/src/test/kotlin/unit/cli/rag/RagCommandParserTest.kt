package io.averkhogliad.ai.challenge.week4.cli.unit.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommand
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class RagCommandParserTest : FreeSpec({

    "parse" - {

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
    }
})
