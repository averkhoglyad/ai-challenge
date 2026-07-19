package io.averkhogliad.ai.challenge.indexer.infrastructure.chunker

import io.averkhogliad.ai.challenge.indexer.domain.model.Chunk
import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategyType
import java.util.*

class StructuralChunker(
    private val maxSectionSize: Int = 10_000,
) : ChunkingStrategy {
    override val type = ChunkingStrategyType.STRUCTURAL

    override fun chunk(document: Document): List<Chunk> {
        val extension = document.path.toFile().extension.lowercase()
        return when (extension) {
            "md", "markdown" -> chunkMarkdown(document)
            else -> chunkByParagraphs(document)
        }
    }

    private fun chunkMarkdown(document: Document): List<Chunk> {
        val sections = mutableListOf<Chunk>()
        val lines = document.content.lines()
        var currentSection = StringBuilder()
        var currentTitle: String? = null

        for (line in lines) {
            if (line.startsWith("#")) {
                if (currentSection.isNotEmpty()) {
                    sections.add(createChunk(document, currentSection.toString(), currentTitle))
                    currentSection.clear()
                }
                currentTitle = line.trimStart('#').trim()
            } else {
                currentSection.appendLine(line)
            }
        }

        if (currentSection.isNotEmpty()) {
            sections.add(createChunk(document, currentSection.toString(), currentTitle))
        }

        return sections
    }

    private fun chunkByParagraphs(document: Document): List<Chunk> {
        return document.content
            .split("\n\n")
            .filter { it.isNotBlank() }
            .mapIndexed { index, paragraph ->
                createChunk(document, paragraph, "Paragraph ${index + 1}")
            }
    }

    private fun createChunk(document: Document, text: String, title: String?): Chunk {
        return Chunk(
            id = UUID.randomUUID(),
            text = text,
            source = document.path.toString(),
            title = title,
            metadata = document.metadata,
        )
    }
}
