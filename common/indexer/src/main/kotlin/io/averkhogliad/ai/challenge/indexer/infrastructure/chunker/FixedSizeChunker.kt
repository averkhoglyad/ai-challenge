package io.averkhogliad.ai.challenge.indexer.infrastructure.chunker

import io.averkhogliad.ai.challenge.indexer.domain.model.Chunk
import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategyType
import java.util.*

class FixedSizeChunker(
    private val chunkSize: Int = 500,
    private val overlap: Int = 50,
) : ChunkingStrategy {
    init {
        require(chunkSize > overlap) {
            "chunkSize ($chunkSize) must be greater than overlap ($overlap)"
        }
    }

    override val type = ChunkingStrategyType.FIXED_SIZE

    override fun chunk(document: Document): List<Chunk> {
        val text = document.content
        if (text.length <= chunkSize) {
            return listOf(createChunk(document, text, 0, 0))
        }

        val chunks = mutableListOf<Chunk>()
        var start = 0

        while (start < text.length) {
            val end = minOf(start + chunkSize, text.length)
            val chunkText = text.substring(start, end)
            chunks.add(createChunk(document, chunkText, chunks.size, start))
            start += chunkSize - overlap
            if (end == text.length) break
        }

        return chunks
    }

    private fun createChunk(document: Document, text: String, index: Int, startOffset: Int): Chunk {
        val endOffset = startOffset + text.length
        val startLine = document.content.substring(0, startOffset).count { it == '\n' } + 1
        val endLine = document.content.substring(0, endOffset).count { it == '\n' } + 1
        return Chunk(
            id = UUID.randomUUID(),
            text = text,
            source = document.path.toString(),
            metadata = document.metadata + mapOf(
                "chunk_index" to index.toString(),
                "start_line" to startLine.toString(),
                "end_line" to endLine.toString(),
            ),
        )
    }
}
