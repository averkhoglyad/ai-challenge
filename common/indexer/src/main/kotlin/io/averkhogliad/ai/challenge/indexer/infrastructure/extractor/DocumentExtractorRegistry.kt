package io.averkhogliad.ai.challenge.indexer.infrastructure.extractor

import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import io.averkhogliad.ai.challenge.indexer.domain.port.DocumentExtractor
import java.nio.file.Path

class DocumentExtractorRegistry(
    extractors: List<DocumentExtractor>,
) {
    private val extractorMap: Map<String, DocumentExtractor> = extractors.flatMap { extractor ->
        extractor.supportedExtensions.map { ext -> ext.lowercase() to extractor }
    }.toMap()

    val supportedExtensions: Set<String> = extractorMap.keys

    fun getExtractor(path: Path): DocumentExtractor? {
        val extension = path.toFile().extension.lowercase()
        return extractorMap[extension]
    }

    suspend fun extract(path: Path): Document? {
        return getExtractor(path)?.extract(path)
    }
}
