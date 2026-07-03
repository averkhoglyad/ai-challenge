package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.indexer.extractor

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Document
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.DocumentType
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor.MarkdownExtractor
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class MarkdownExtractorTest : FreeSpec({

    lateinit var extractor: MarkdownExtractor

    beforeEach {
        extractor = MarkdownExtractor()
    }

    "extract" - {

        "should strip bold markers" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/test.md",
                    type = DocumentType.MARKDOWN,
                    contentHash = "hash",
                    rawContent = "This is **bold text** in a sentence."
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "This is bold text in a sentence."
            }
        }

        "should strip italic markers" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/test.md",
                    type = DocumentType.MARKDOWN,
                    contentHash = "hash",
                    rawContent = "This is *italic text* here."
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "This is italic text here."
            }
        }

        "should strip links keeping anchor text" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/test.md",
                    type = DocumentType.MARKDOWN,
                    contentHash = "hash",
                    rawContent = "Click [here](https://example.com) for more."
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "Click here for more."
            }
        }

        "should strip images keeping alt text" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/test.md",
                    type = DocumentType.MARKDOWN,
                    contentHash = "hash",
                    rawContent = "Image: ![logo](https://example.com/img.png)"
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "Image: logo"
            }
        }

        "should strip code blocks keeping content" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/test.md",
                    type = DocumentType.MARKDOWN,
                    contentHash = "hash",
                    rawContent = "Some text\n```kotlin\nfun main() = println(\"Hi\")\n```\nMore text"
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "Some text\nfun main() = println(\"Hi\")\nMore text"
            }
        }

        "should preserve headings in metadata" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/test.md",
                    type = DocumentType.MARKDOWN,
                    contentHash = "hash",
                    rawContent = "## Introduction\nSome text\n### Details\nMore text"
                )

                // when
                val result = extractor.extract(document)

                // then
                result.metadata["headings"] shouldBe "## Introduction\n### Details"
                result.content shouldBe "Introduction\nSome text\nDetails\nMore text"
            }
        }

        "should handle empty document" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/empty.md",
                    type = DocumentType.MARKDOWN,
                    contentHash = "empty",
                    rawContent = ""
                )

                // when
                val result = extractor.extract(document)

                // then
                result.documentPath shouldBe "/docs/empty.md"
                result.content shouldBe ""
                result.metadata["type"] shouldBe "MARKDOWN"
            }
        }
    }

    "canHandle" - {

        "should return true for MARKDOWN" {
            // when & then
            extractor.canHandle(DocumentType.MARKDOWN) shouldBe true
        }

        "should return false for PLAIN_TEXT" {
            // when & then
            extractor.canHandle(DocumentType.PLAIN_TEXT) shouldBe false
        }

        "should return false for HTML" {
            // when & then
            extractor.canHandle(DocumentType.HTML) shouldBe false
        }
    }
})
