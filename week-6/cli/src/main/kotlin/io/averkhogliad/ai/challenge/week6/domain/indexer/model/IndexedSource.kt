package io.averkhogliad.ai.challenge.week6.domain.indexer.model

import java.nio.file.Path
import java.time.Instant

data class IndexedSource(
    val id: String,
    val projectId: String,
    val path: Path,
    val sourceType: SourceType,
    val isDefault: Boolean,
    val createdAt: Instant,
)

enum class SourceType {
    FILE,
    DIRECTORY,
}
