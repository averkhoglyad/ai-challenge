package io.averkhogliad.ai.challenge.week6.domain.model

import java.nio.file.Path

data class ProjectContext(
    val projectId: String,
    val rootPath: Path,
    val docsPaths: List<Path>,
    val isGitEnabled: Boolean
)
