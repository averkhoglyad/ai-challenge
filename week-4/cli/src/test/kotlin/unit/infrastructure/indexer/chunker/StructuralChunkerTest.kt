package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.indexer.chunker

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ExtractedText
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.chunker.StructuralChunker
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.util.*

class StructuralChunkerTest : FreeSpec({

    val runId = UUID.randomUUID()

    "chunk" - {

        "should split markdown by ## and ### headings when headings metadata present" {
            // given
            val chunker = StructuralChunker()
            val text =
                "## Introduction\nIntro content here.\n\n## Chapter 1\nChapter content.\n\n### Section 1.1\nSection content."
            val extractedText = ExtractedText(
                documentPath = "/docs/structure.md",
                content = text,
                metadata = mapOf(
                    "type" to "MARKDOWN",
                    "headings" to "## Introduction\n## Chapter 1\n### Section 1.1"
                )
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then
            chunks.size shouldBe 3
            chunks[0].section shouldBe "## Introduction"
            chunks[1].section shouldBe "## Chapter 1"
            chunks[2].section shouldBe "### Section 1.1"
            chunks.all { it.strategy == ChunkingStrategyType.STRUCTURAL } shouldBe true
        }

        "should split plain text by paragraphs (double newlines)" {
            // given
            val chunker = StructuralChunker()
            val text = "Paragraph one.\n\nParagraph two.\n\nParagraph three."
            val extractedText = ExtractedText(
                documentPath = "/docs/paragraphs.txt",
                content = text,
                metadata = mapOf("type" to "PLAIN_TEXT")
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then
            chunks.size shouldBe 1  // paragraphs merged into one buffer if total < 2000
            chunks[0].text shouldBe text
        }

        "should split long sections > 2000 chars further" {
            // given
            val maxLen = 100
            val chunker = StructuralChunker(maxSectionLength = maxLen)
            // Create a very long section under a single heading
            val longContent = "word ".repeat(maxLen * 3) // ~300 characters
            val text = "## Big Section\n$longContent"
            val extractedText = ExtractedText(
                documentPath = "/docs/longsection.md",
                content = text,
                metadata = mapOf(
                    "type" to "MARKDOWN",
                    "headings" to "## Big Section"
                )
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then — should produce multiple chunks for the long section
            chunks.size shouldBeGreaterThan 1
            // All chunks should have the same section heading
            chunks.all { it.section == "## Big Section" } shouldBe true
        }

        "should set section field correctly in chunk" {
            // given
            val chunker = StructuralChunker()
            val text =
                "## Features\nFeature list content.\n\n### Performance\nPerformance details.\n\n## Limitations\nLimitation notes."
            val extractedText = ExtractedText(
                documentPath = "/docs/features.md",
                content = text,
                metadata = mapOf(
                    "type" to "MARKDOWN",
                    "headings" to "## Features\n### Performance\n## Limitations"
                )
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then
            chunks.size shouldBe 3
            val sections = chunks.map { it.section }
            sections shouldBe listOf("## Features", "### Performance", "## Limitations")
        }

        "should assign unique UUIDs to each chunk" {
            // given
            val chunker = StructuralChunker()
            val text = "## A\nContent A\n\n## B\nContent B\n\n## C\nContent C"
            val extractedText = ExtractedText(
                documentPath = "/docs/ids.md",
                content = text,
                metadata = mapOf(
                    "type" to "MARKDOWN",
                    "headings" to "## A\n## B\n## C"
                )
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then
            val ids = chunks.map { it.id }
            ids.toSet().size shouldBe ids.size
        }

        "should handle empty headings metadata as plain text" {
            // given
            val chunker = StructuralChunker()
            val text = "Line 1\n\nLine 2\n\nLine 3"
            val extractedText = ExtractedText(
                documentPath = "/docs/nopara.txt",
                content = text,
                metadata = emptyMap() // no headings
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then — should fall back to paragraph-based chunking
            chunks.isNotEmpty() shouldBe true
            chunks[0].text shouldBe text
        }

        "should return non-empty result for any input" {
            // given
            val chunker = StructuralChunker()
            val extractedText = ExtractedText(
                documentPath = "/docs/min.txt",
                content = "Minimal",
                metadata = emptyMap()
            )

            // when
            val chunks = chunker.chunk(extractedText, runId)

            // then
            chunks.size shouldBe 1
            chunks[0].text shouldBe "Minimal"
        }
    }
})
