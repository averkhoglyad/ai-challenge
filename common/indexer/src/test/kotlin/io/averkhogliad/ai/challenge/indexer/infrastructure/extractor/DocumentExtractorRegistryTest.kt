package io.averkhogliad.ai.challenge.indexer.infrastructure.extractor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path

class DocumentExtractorRegistryTest : FreeSpec({

    "DocumentExtractorRegistry" - {

        "resolves extractor by file extension" {
            val registry = DocumentExtractorRegistry(listOf(TextExtractor(), MarkdownExtractor()))

            val txtExtractor = registry.getExtractor(Path.of("test.txt"))
            val mdExtractor = registry.getExtractor(Path.of("test.md"))

            txtExtractor.shouldBeInstanceOf<TextExtractor>()
            mdExtractor.shouldBeInstanceOf<MarkdownExtractor>()
            txtExtractor.supportedExtensions.contains("txt") shouldBe true
            mdExtractor.supportedExtensions.contains("md") shouldBe true
        }

        "returns null for unknown extension" {
            val registry = DocumentExtractorRegistry(listOf(TextExtractor()))
            val unknownFile = Path.of("test.xyz")

            registry.getExtractor(unknownFile) shouldBe null
        }

        "supportedExtensions returns all registered extensions" {
            val registry = DocumentExtractorRegistry(listOf(TextExtractor(), MarkdownExtractor()))

            val extensions = registry.supportedExtensions

            extensions shouldBe setOf("txt", "text", "md", "markdown")
        }
    }
})
