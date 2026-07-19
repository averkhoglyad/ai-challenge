package io.averkhogliad.ai.challenge.indexer.infrastructure.extractor

import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import io.averkhogliad.ai.challenge.indexer.domain.port.DocumentExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

class TextExtractor : DocumentExtractor {
    override val supportedExtensions = setOf("txt", "text")

    override suspend fun extract(path: Path): Document = withContext(Dispatchers.IO) {
        val content = Files.readString(path)
        Document(
            path = path,
            content = content,
            metadata = mapOf(
                "type" to "text",
                "size" to Files.size(path).toString(),
            ),
        )
    }
}
