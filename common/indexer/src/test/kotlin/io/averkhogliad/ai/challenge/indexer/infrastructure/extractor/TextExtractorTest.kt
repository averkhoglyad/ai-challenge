package io.averkhogliad.ai.challenge.indexer.infrastructure.extractor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.writeText

class TextExtractorTest : FreeSpec({

    "TextExtractor" - {

        "extracts content from txt file" {
            val tmpFile = Files.createTempFile("test", ".txt")
            try {
                tmpFile.writeText("Hello, world!")
                val extractor = TextExtractor()

                val doc = extractor.extract(tmpFile)

                doc.content shouldBe "Hello, world!"
                doc.metadata["type"] shouldBe "text"
            } finally {
                Files.deleteIfExists(tmpFile)
            }
        }

        "supportedExtensions includes txt and text" {
            val extractor = TextExtractor()
            extractor.supportedExtensions shouldBe setOf("txt", "text")
        }
    }
})
