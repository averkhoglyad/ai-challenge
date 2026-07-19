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
            return listOf(createChunk(document, text, 0))
        }

        val chunks = mutableListOf<Chunk>()
        var start = 0

        while (start < text.length) {
            val end = minOf(start + chunkSize, text.length)
            val chunkText = text.substring(start, end)
            chunks.add(createChunk(document, chunkText, chunks.size))
            start += chunkSize - overlap
            if (end == text.length) break
        }

        return chunks
    }

    private fun createChunk(document: Document, text: String, index: Int): Chunk {
        return Chunk(
            id = UUID.randomUUID(),
            text = text,
            source = document.path.toString(),
            metadata = document.metadata + ("chunk_index" to index.toString()),
        )
    }
}
