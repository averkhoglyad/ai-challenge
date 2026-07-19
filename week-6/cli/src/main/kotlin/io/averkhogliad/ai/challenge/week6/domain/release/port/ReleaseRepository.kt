package io.averkhogliad.ai.challenge.week6.domain.release.port

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.release.model.Release

interface ReleaseRepository {
    suspend fun save(release: Release): DomainResult<Unit>
    suspend fun findById(id: String): DomainResult<Release?>
    suspend fun findByProjectIdAndVersion(projectId: String, version: String): DomainResult<Release?>
    suspend fun findByProjectId(projectId: String, limit: Int = 10): DomainResult<List<Release>>
    suspend fun findLatestByProjectId(projectId: String): DomainResult<Release?>
    suspend fun delete(id: String): DomainResult<Unit>
}
