package io.averkhogliad.ai.challenge.week6.domain.port

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import java.nio.file.Path

interface GitPort {
    suspend fun getCurrentBranch(rootPath: Path): DomainResult<String>
    suspend fun getCurrentCommit(rootPath: Path): DomainResult<String>
    suspend fun checkGitStatus(rootPath: Path): DomainResult<Boolean>
    suspend fun getDiffBetweenBranches(rootPath: Path, sourceBranch: String, targetBranch: String): DomainResult<String>
    suspend fun getDiffBetweenCommits(rootPath: Path, base: String, head: String): DomainResult<String>
    suspend fun getLastCommitHash(rootPath: Path): DomainResult<String>
    suspend fun isMergeCommit(rootPath: Path): DomainResult<Boolean>
    suspend fun branchExists(rootPath: Path, branch: String): DomainResult<Boolean>
    suspend fun getCommitsBetween(
        rootPath: Path,
        base: String?,
        head: String = "HEAD",
        limit: Int = 500
    ): DomainResult<String>
}
