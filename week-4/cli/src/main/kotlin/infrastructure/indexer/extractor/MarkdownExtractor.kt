package io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Document
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.DocumentType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ExtractedText
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.DocumentExtractor

/**
 * Экстрактор для Markdown (.md) файлов.
 *
 * Удаляет MD-синтаксис (заголовки, ссылки, блоки кода, изображения),
 * сохраняя структуру заголовков в metadata.
 */
class MarkdownExtractor : DocumentExtractor {

    override suspend fun extract(document: Document): ExtractedText {
        val raw = document.rawContent
        val headings = extractHeadings(raw)
        val clean = stripMarkdownSyntax(raw)

        return ExtractedText(
            documentPath = document.path,
            content = clean,
            metadata = mapOf(
                "type" to DocumentType.MARKDOWN.name,
                "headings" to headings.joinToString("\n")
            )
        )
    }

    override fun canHandle(type: DocumentType): Boolean =
        type == DocumentType.MARKDOWN

    // ──── Private helpers ────

    /**
     * Извлекает заголовки ## и ### для сохранения структуры документа.
     */
    private fun extractHeadings(text: String): List<String> {
        return HEADING_REGEX.findAll(text)
            .map { it.value.trim() }
            .toList()
    }

    /**
     * Удаляет markdown-синтаксис, оставляя чистый текст.
     */
    private fun stripMarkdownSyntax(text: String): String {
        var cleaned = text
        // Удаляем HTML-комментарии
        cleaned = HTML_COMMENT_REGEX.replace(cleaned, "")
        // Удаляем изображения ![alt](url)
        cleaned = IMAGE_REGEX.replace(cleaned, "$1")
        // Заменяем ссылки [text](url) на text
        cleaned = LINK_REGEX.replace(cleaned, "$1")
        // Удаляем code blocks (``` ... ```)
        cleaned = CODE_BLOCK_REGEX.replace(cleaned) { match ->
            val code = match.groupValues[1].trim()
            // Сохраняем содержимое кода (без обрамления)
            code
        }
        // Удаляем инлайн-код (`code`)
        cleaned = INLINE_CODE_REGEX.replace(cleaned, "$1")
        // Удаляем символы форматирования (**, __, *, _)
        cleaned = BOLD_REGEX.replace(cleaned, "$1")
        cleaned = ITALIC_REGEX.replace(cleaned, "$1")
        // Удаляем заголовочные # (оставляем текст заголовка)
        cleaned = HEADING_LINE_REGEX.replace(cleaned, "$1")
        // Удаляем горизонтальные линии
        cleaned = HR_REGEX.replace(cleaned, "")
        // Удаляем blockquotes >
        cleaned = BLOCKQUOTE_REGEX.replace(cleaned, "$1")
        // Убираем лишние пустые строки
        cleaned = MULTILINE_BLANK_REGEX.replace(cleaned, "\n\n")
        return cleaned.trim()
    }

    companion object {
        private val HEADING_REGEX = Regex("^#{2,3}\\s+.+$", RegexOption.MULTILINE)
        private val HEADING_LINE_REGEX = Regex("^#{1,6}\\s+(.+)$", RegexOption.MULTILINE)
        private val HTML_COMMENT_REGEX = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        private val IMAGE_REGEX = Regex("!\\[(.*?)]\\(.*?\\)")
        private val LINK_REGEX = Regex("\\[(.*?)]\\(.*?\\)")
        private val CODE_BLOCK_REGEX = Regex("```[^\\n]*\\n([\\s\\S]*?)```")
        private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
        private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
        private val ITALIC_REGEX = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
        private val HR_REGEX = Regex("^-{3,}\\s*$", RegexOption.MULTILINE)
        private val BLOCKQUOTE_REGEX = Regex("^>\\s?(.+)$", RegexOption.MULTILINE)
        private val MULTILINE_BLANK_REGEX = Regex("\n{3,}")
    }
}
