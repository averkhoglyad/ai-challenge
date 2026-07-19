package io.averkhogliad.ai.challenge.indexer.infrastructure.extractor

import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import io.averkhogliad.ai.challenge.indexer.domain.port.DocumentExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

class MarkdownExtractor : DocumentExtractor {
    override val supportedExtensions = setOf("md", "markdown")

    override suspend fun extract(path: Path): Document = withContext(Dispatchers.IO) {
        val content = Files.readString(path)
        val title = content.lines()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
        Document(
            path = path,
            content = content,
            metadata = mapOf(
                "type" to "markdown",
                "title" to (title ?: ""),
            ),
        )
    }
}
