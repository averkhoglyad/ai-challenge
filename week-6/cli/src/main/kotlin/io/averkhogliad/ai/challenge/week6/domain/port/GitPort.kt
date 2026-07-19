package io.averkhogliad.ai.challenge.week6.domain.port

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import java.nio.file.Path

interface GitPort {
    suspend fun getCurrentBranch(rootPath: Path): DomainResult<String>
    suspend fun getCurrentCommit(rootPath: Path): DomainResult<String>
    suspend fun checkGitStatus(rootPath: Path): DomainResult<Boolean>
}
