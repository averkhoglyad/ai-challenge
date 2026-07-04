package io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Document
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.DocumentType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ExtractedText
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.DocumentExtractor

/**
 * Экстрактор для HTML файлов.
 *
 * Удаляет HTML-теги, атрибуты, скрипты и стили,
 * оставляя только текстовое содержимое.
 */
class HtmlExtractor : DocumentExtractor {

    override suspend fun extract(document: Document): ExtractedText {
        val clean = stripHtml(document.rawContent)

        return ExtractedText(
            documentPath = document.path,
            content = clean,
            metadata = mapOf("type" to DocumentType.HTML.name)
        )
    }

    override fun canHandle(type: DocumentType): Boolean =
        type == DocumentType.HTML

    // ──── Private helpers ────

    /**
     * Удаляет HTML-теги, скрипты и стили, возвращая чистый текст.
     */
    private fun stripHtml(html: String): String {
        var cleaned = html
        // Удаляем <script>...</script>
        cleaned = SCRIPT_REGEX.replace(cleaned, "")
        // Удаляем <style>...</style>
        cleaned = STYLE_REGEX.replace(cleaned, "")
        // Удаляем HTML-комментарии <!-- ... -->
        cleaned = COMMENT_REGEX.replace(cleaned, "")
        // Заменяем <br> и <br/> на перенос строки
        cleaned = BR_REGEX.replace(cleaned, "\n")
        // Заменяем </p>, </div>, </h1>... на двойной перенос
        cleaned = BLOCK_CLOSE_REGEX.replace(cleaned, "\n\n")
        // Удаляем все оставшиеся HTML-теги
        cleaned = TAG_REGEX.replace(cleaned, "")
        // Убираем лишние пустые строки
        cleaned = MULTILINE_BLANK_REGEX.replace(cleaned, "\n\n")
        // Убираем пробелы в начале и конце строк
        cleaned = cleaned.lines()
            .joinToString("\n") { it.trim() }
        // Декодируем HTML entities (&amp; &lt; &gt; &quot; &#39; &nbsp;) — после trim,
        // чтобы пробелы от &nbsp; не были удалены per-line trim'ом
        cleaned = cleaned
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        // Убираем только висячие переводы строк, сохраняя значимые пробелы
        return cleaned.trim('\n')
    }

    companion object {
        private val SCRIPT_REGEX = Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
        private val STYLE_REGEX = Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
        private val COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")
        private val BR_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
        private val BLOCK_CLOSE_REGEX = Regex("</(p|div|h[1-6]|section|article|li|tr)>", RegexOption.IGNORE_CASE)
        private val TAG_REGEX = Regex("<[^>]*>")
        private val MULTILINE_BLANK_REGEX = Regex("\n{3,}")
    }
}
