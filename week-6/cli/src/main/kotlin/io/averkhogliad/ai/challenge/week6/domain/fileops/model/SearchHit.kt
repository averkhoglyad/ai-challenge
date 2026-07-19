package io.averkhogliad.ai.challenge.week6.domain.fileops.model

data class SearchHit(
    val path: RelativePath,
    val line: Int,
    val snippet: String,
    val contextBefore: List<String> = emptyList(),
    val contextAfter: List<String> = emptyList(),
)
