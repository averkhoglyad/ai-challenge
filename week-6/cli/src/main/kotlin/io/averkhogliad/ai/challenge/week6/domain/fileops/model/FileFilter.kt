package io.averkhogliad.ai.challenge.week6.domain.fileops.model

data class FileFilter(
    val extension: String? = null,
    val namePattern: String? = null,
    val excludeDirs: List<String> = emptyList(),
)
