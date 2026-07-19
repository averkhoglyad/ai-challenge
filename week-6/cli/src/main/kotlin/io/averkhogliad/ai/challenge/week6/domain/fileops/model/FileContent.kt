package io.averkhogliad.ai.challenge.week6.domain.fileops.model

import java.nio.charset.Charset

data class FileContent(
    val path: RelativePath,
    val content: String,
    val encoding: Charset = Charsets.UTF_8,
    val sizeBytes: Long,
    val truncated: Boolean = false,
)
