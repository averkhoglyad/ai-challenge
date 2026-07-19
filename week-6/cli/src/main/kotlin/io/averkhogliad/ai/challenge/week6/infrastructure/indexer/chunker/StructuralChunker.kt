package io.averkhogliad.ai.challenge.week6.infrastructure.indexer.chunker

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.ExtractedText
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.ChunkingStrategy
import java.security.MessageDigest
import java.util.*

/**
 * Стратегия разбиения текста по структуре документа.
 *
 * - Markdown: разбивает по заголовкам ## и ###
 * - Plain Text: разбивает по двойным переносам строк (абзацы), объединяя
 *   смежные короткие абзацы
 * - HTML: разбивает по двойным переносам строк (аналогично Plain Text)
 *
 * Длинные секции (> [maxSectionLength]) дополнительно разбиваются
 * с сохранением заголовка в metadata.
 *
 * @param maxSectionLength максимальная длина секции до принудительного разбиения
 */
class StructuralChunker(
    private val maxSectionLength: Int = DEFAULT_MAX_SECTION_LENGTH
) : ChunkingStrategy {

    override val type: ChunkingStrategyType = ChunkingStrategyType.STRUCTURAL

    override fun chunk(extractedText: ExtractedText, runId: UUID): List<Chunk> {
        val text = extractedText.content
        val source = extractedText.documentPath
        val title = source.substringAfterLast('/').substringAfterLast('\\')

        val headings = extractedText.metadata["headings"]?.lines()?.filter { it.isNotBlank() } ?: emptyList()

        return if (headings.isNotEmpty()) {
            chunkByHeadings(text, headings, source, title, runId)
        } else {
            chunkByParagraphs(text, source, title, runId)
        }
    }

    private fun chunkByHeadings(
        text: String,
        headings: List<String>,
        source: String,
        title: String?,
        runId: UUID
    ): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        val sections = splitByHeadings(text, headings)

        for ((heading, sectionText) in sections) {
            val trimmed = sectionText.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.length > maxSectionLength) {
                val subChunks = splitLongSection(trimmed, maxSectionLength)
                subChunks.forEach { subText ->
                    chunks.add(buildChunk(subText, source, title, heading, runId))
                }
            } else {
                chunks.add(buildChunk(trimmed, source, title, heading, runId))
            }
        }

        return chunks
    }

    private fun chunkByParagraphs(
        text: String,
        source: String,
        title: String?,
        runId: UUID
    ): List<Chunk> {
        val paragraphs = text.split(PARAGRAPH_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val chunks = mutableListOf<Chunk>()
        val buffer = StringBuilder()
        val sectionLabel = paragraphs.firstOrNull()?.take(50) ?: ""

        for (para in paragraphs) {
            if (buffer.length + para.length > maxSectionLength && buffer.isNotEmpty()) {
                chunks.add(buildChunk(buffer.toString().trim(), source, title, sectionLabel, runId))
                buffer.clear()
            }
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(para)
        }

        if (buffer.isNotEmpty()) {
            chunks.add(buildChunk(buffer.toString().trim(), source, title, sectionLabel, runId))
        }

        return chunks.ifEmpty {
            listOf(buildChunk(text, source, title, null, runId))
        }
    }

    private fun splitByHeadings(text: String, headings: List<String>): List<Pair<String?, String>> {
        val result = mutableListOf<Pair<String?, String>>()

        if (headings.isEmpty()) {
            result.add(null to text)
            return result
        }

        val escapedHeadings = headings.map { Regex.escape(it) }
        val headingPattern = Regex("^(${escapedHeadings.joinToString("|") { it }})\\s*$", RegexOption.MULTILINE)

        val matches = headingPattern.findAll(text).toList()

        if (matches.isEmpty()) {
            result.add(null to text)
            return result
        }

        val beforeFirst = text.substring(0, matches.first().range.first).trim()
        if (beforeFirst.isNotEmpty()) {
            result.add(null to beforeFirst)
        }

        for (i in matches.indices) {
            val match = matches[i]
            val heading = match.groupValues[1]
            val contentStart = match.range.last + 1
            val contentEnd = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val content = text.substring(contentStart, contentEnd).trim()
            result.add(heading to content)
        }

        return result
    }

    private fun splitLongSection(text: String, maxLen: Int): List<String> {
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxLen) {
            val cutPos = remaining.lastIndexOf(' ', maxLen)
            val splitPos = if (cutPos > 0) cutPos else maxLen
            result.add(remaining.substring(0, splitPos).trim())
            remaining = remaining.substring(splitPos).trim()
        }
        if (remaining.isNotEmpty()) {
            result.add(remaining)
        }
        return result
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
        const val DEFAULT_MAX_SECTION_LENGTH = 2000
        private val PARAGRAPH_SPLIT_REGEX = Regex("\n\n|\n\\s*\n")

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
