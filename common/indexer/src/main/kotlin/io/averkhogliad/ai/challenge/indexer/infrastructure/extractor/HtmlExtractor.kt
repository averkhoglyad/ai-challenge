package io.averkhogliad.ai.challenge.indexer.infrastructure.extractor

import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import io.averkhogliad.ai.challenge.indexer.domain.port.DocumentExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Path

class HtmlExtractor : DocumentExtractor {
    override val supportedExtensions = setOf("html", "htm")

    override suspend fun extract(path: Path): Document = withContext(Dispatchers.IO) {
        val html = Files.readString(path)
        val doc = Jsoup.parse(html)
        val title = doc.title()
        val text = doc.body().text()
        Document(
            path = path,
            content = text,
            metadata = mapOf(
                "type" to "html",
                "title" to title,
            ),
        )
    }
}
