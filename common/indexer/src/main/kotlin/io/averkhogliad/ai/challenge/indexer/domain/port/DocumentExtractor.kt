package io.averkhogliad.ai.challenge.indexer.domain.port

import io.averkhogliad.ai.challenge.indexer.domain.model.Document
import java.nio.file.Path

interface DocumentExtractor {
    val supportedExtensions: Set<String>

    suspend fun extract(path: Path): Document
}
