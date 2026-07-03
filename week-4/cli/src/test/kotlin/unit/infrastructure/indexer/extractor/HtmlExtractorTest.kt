package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.indexer.extractor

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Document
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.DocumentType
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor.HtmlExtractor
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class HtmlExtractorTest : FreeSpec({

    lateinit var extractor: HtmlExtractor

    beforeEach {
        extractor = HtmlExtractor()
    }

    "extract" - {

        "should strip HTML tags keeping text content" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/page.html",
                    type = DocumentType.HTML,
                    contentHash = "hash",
                    rawContent = "<h1>Title</h1><p>Hello <b>world</b></p>"
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "Title\n\nHello world"
            }
        }

        "should remove script tags and their content" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/page.html",
                    type = DocumentType.HTML,
                    contentHash = "hash",
                    rawContent = "<p>Before</p><script>alert('xss')</script><p>After</p>"
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "Before\n\nAfter"
            }
        }

        "should remove style tags and their content" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/page.html",
                    type = DocumentType.HTML,
                    contentHash = "hash",
                    rawContent = "<p>Visible</p><style>body { color: red; }</style><p>Content</p>"
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "Visible\n\nContent"
            }
        }

        "should decode HTML entities" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/page.html",
                    type = DocumentType.HTML,
                    contentHash = "hash",
                    rawContent = "<p>&amp; &lt; &gt; &quot; &#39; &nbsp;</p>"
                )

                // when
                val result = extractor.extract(document)

                // then
                result.content shouldBe "& < > \" '  "
            }
        }

        "should handle empty document" {
            runTest {
                // given
                val document = Document(
                    path = "/docs/empty.html",
                    type = DocumentType.HTML,
                    contentHash = "empty",
                    rawContent = ""
                )

                // when
                val result = extractor.extract(document)

                // then
                result.documentPath shouldBe "/docs/empty.html"
                result.content shouldBe ""
                result.metadata["type"] shouldBe "HTML"
            }
        }
    }

    "canHandle" - {

        "should return true for HTML" {
            // when & then
            extractor.canHandle(DocumentType.HTML) shouldBe true
        }

        "should return false for PLAIN_TEXT" {
            // when & then
            extractor.canHandle(DocumentType.PLAIN_TEXT) shouldBe false
        }

        "should return false for MARKDOWN" {
            // when & then
            extractor.canHandle(DocumentType.MARKDOWN) shouldBe false
        }
    }
})
