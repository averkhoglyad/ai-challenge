package io.averkhogliad.ai.challenge.week6.domain.fileops.model

data class SearchQuery(
    val query: String,
    val ignoreCase: Boolean = true,
    val extension: String? = null,
    val inDirectory: RelativePath? = null,
)
