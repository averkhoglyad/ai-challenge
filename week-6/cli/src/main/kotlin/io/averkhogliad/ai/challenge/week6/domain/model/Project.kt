package io.averkhogliad.ai.challenge.week6.domain.model

import java.nio.file.Path
import java.time.Instant

data class Project(
    val id: String,
    val name: String,
    val rootPath: Path,
    val docsPath: Path? = null,
    val faqPath: Path? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
