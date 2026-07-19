package io.averkhogliad.ai.challenge.week6.domain.fileops.model

data class FileChange(
    val path: RelativePath,
    val oldContent: String?,
    val newContent: String,
)
