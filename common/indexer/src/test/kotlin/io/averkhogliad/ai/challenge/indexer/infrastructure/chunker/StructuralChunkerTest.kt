package io.averkhogliad.ai.challenge.indexer.infrastructure.chunker

import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategyType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class StructuralChunkerTest : FreeSpec({

    "StructuralChunker" - {

        "splits markdown by headings" {
            val chunker = StructuralChunker()
            val content = "# Title1\nContent one.\n# Title2\nContent two."
            val doc = Document(Path.of("test.md"), content)

            val chunks = chunker.chunk(doc)

            chunks shouldHaveSize 2
            chunks[0].title shouldBe "Title1"
            chunks[1].title shouldBe "Title2"
        }

        "splits plain text by paragraphs" {
            val chunker = StructuralChunker()
            val content = "Paragraph one.\n\nParagraph two."
            val doc = Document(Path.of("test.txt"), content)

            val chunks = chunker.chunk(doc)

            chunks shouldHaveSize 2
        }

        "has correct type" {
            val chunker = StructuralChunker()
            chunker.type shouldBe ChunkingStrategyType.STRUCTURAL
        }
    }
})
