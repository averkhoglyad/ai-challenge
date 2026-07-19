package io.averkhogliad.ai.challenge.week6.domain.fileops.model

import java.time.Instant

data class FileMetadata(
    val path: RelativePath,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val lastModified: Instant,
    val isBinary: Boolean,
)
