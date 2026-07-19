package io.averkhogliad.ai.challenge.week6.infrastructure.indexer.chunker

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.ExtractedText
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.ChunkingStrategy
import java.security.MessageDigest
import java.util.*

/**
 * Стратегия разбиения текста на чанки фиксированного размера с перекрытием.
 *
 * Использует sliding window: окно размером [chunkSize] сдвигается
 * на [chunkSize] - [overlap]. Границы чанков выравниваются по пробелам,
 * чтобы не разрывать слова.
 *
 * @param chunkSize размер чанка в символах
 * @param overlap размер перекрытия между соседними чанками
 */
class FixedSizeChunker(
    private val chunkSize: Int,
    private val overlap: Int
) : ChunkingStrategy {

    override val type: ChunkingStrategyType = ChunkingStrategyType.FIXED_SIZE

    override fun chunk(extractedText: ExtractedText, runId: UUID): List<Chunk> {
        val text = extractedText.content
        val source = extractedText.documentPath
        val title = source.substringAfterLast('/').substringAfterLast('\\')

        if (text.length <= chunkSize) {
            return listOf(buildChunk(text, source, title, null, runId))
        }

        val step = chunkSize - overlap
        val chunks = mutableListOf<Chunk>()
        var start = 0

        while (start < text.length) {
            var end = (start + chunkSize).coerceAtMost(text.length)

            // Выравниваем границу по пробелу/знаку препинания (не разрываем слова)
            if (end < text.length) {
                val lastSpace = text.lastIndexOf(' ', end)
                val lastPunct = text.lastIndexOf('.', end)
                val lastNewline = text.lastIndexOf('\n', end)
                val boundary = maxOf(lastSpace, lastPunct, lastNewline)
                if (boundary > start) {
                    end = boundary + 1
                }
            }

            val chunkText = text.substring(start, end).trim()
            if (chunkText.isNotEmpty()) {
                chunks.add(buildChunk(chunkText, source, title, null, runId))
            }

            start += step
            if (start >= text.length) break
        }

        return chunks
    }

    private fun buildChunk(
        text: String,
        source: String,
        title: String?,
        section: String?,
        runId: UUID
    ): Chunk {
        val contentHash = sha256(source + (section ?: "") + text)
        return Chunk(
            id = UUID.randomUUID(),
            runId = runId,
            contentHash = contentHash,
            source = source,
            title = title,
            section = section,
            text = text,
            strategy = type,
            metadata = emptyMap()
        )
    }

    companion object {
        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
