package io.averkhogliad.ai.challenge.indexer.domain.model

import java.nio.file.Path

data class Document(
    val path: Path,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)
