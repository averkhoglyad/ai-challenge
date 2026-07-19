package io.averkhogliad.ai.challenge.week6.domain.port

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project

interface ProjectRepository {
    suspend fun save(project: Project): DomainResult<Project>
    suspend fun findById(id: String): DomainResult<Project?>
    suspend fun findAll(): DomainResult<List<Project>>
    suspend fun findByPath(path: String): DomainResult<Project?>
    suspend fun delete(id: String): DomainResult<Unit>
}
