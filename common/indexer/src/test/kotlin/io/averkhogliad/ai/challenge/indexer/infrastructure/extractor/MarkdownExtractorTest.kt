package io.averkhogliad.ai.challenge.indexer.infrastructure.extractor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.writeText

class MarkdownExtractorTest : FreeSpec({

    "MarkdownExtractor" - {

        "extracts content and title from md file" {
            val tmpFile = Files.createTempFile("test", ".md")
            try {
                tmpFile.writeText("# My Title\n\nSome content here.")
                val extractor = MarkdownExtractor()

                val doc = extractor.extract(tmpFile)

                doc.content shouldBe "# My Title\n\nSome content here."
                doc.metadata["title"] shouldBe "My Title"
                doc.metadata["type"] shouldBe "markdown"
            } finally {
                Files.deleteIfExists(tmpFile)
            }
        }

        "supportedExtensions includes md and markdown" {
            val extractor = MarkdownExtractor()
            extractor.supportedExtensions shouldBe setOf("md", "markdown")
        }
    }
})
