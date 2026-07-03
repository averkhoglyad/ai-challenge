package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.indexer.extractor

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Document
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.DocumentType
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor.TextExtractor
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class TextExtractorTest : FreeSpec({

    lateinit var extractor: TextExtractor

    beforeEach {
        extractor = TextExtractor()
    }

    "extract" - {

        "should return plain text content unchanged" {
            runTest {
                // given
                val content = "Hello, world!\nThis is plain text."
                val document = Document(
                    path = "/docs/readme.txt",
                    type = DocumentType.PLAIN_TEXT,
                    contentHash = "abc123",
                    rawContent = content
                )

                // when
                val result = extractor.extract(document)

                // then
                result.documentPath shouldBe "/docs/readme.txt"
                result.content shouldBe content
                result.metadata shouldBe mapOf("type" to "PLAIN_TEXT")
            }
        }

        "should handle empty document" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/empty.txt",
                    type = DocumentType.PLAIN_TEXT,
                    contentHash = "empty",
                    rawContent = ""
                )

                // when
                val result = extractor.extract(document)

                // then
                result.documentPath shouldBe "/docs/empty.txt"
                result.content shouldBe ""
                result.metadata shouldBe mapOf("type" to "PLAIN_TEXT")
            }
        }
    }

    "canHandle" - {

        "should return true for PLAIN_TEXT" {
            // when & then
            extractor.canHandle(DocumentType.PLAIN_TEXT) shouldBe true
        }

        "should return false for MARKDOWN" {
            // when & then
            extractor.canHandle(DocumentType.MARKDOWN) shouldBe false
        }

        "should return false for HTML" {
            // when & then
            extractor.canHandle(DocumentType.HTML) shouldBe false
        }
    }
})
