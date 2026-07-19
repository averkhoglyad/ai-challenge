package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class ProjectSettingsRepository {

    fun loadExclusions(projectId: String): List<String> = transaction {
        ProjectsTable.selectAll()
            .where { ProjectsTable.id eq projectId }
            .singleOrNull()
            ?.get(ProjectsTable.exclusions)
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    fun saveExclusions(projectId: String, exclusions: List<String>): DomainResult<Unit> = transaction {
        val affected = ProjectsTable.update({ ProjectsTable.id eq projectId }) {
            it[ProjectsTable.exclusions] = if (exclusions.isEmpty()) null else exclusions.joinToString("\n")
        }
        if (affected == 0) {
            return@transaction DomainResult.Failure(DomainError.projectNotFound(projectId))
        }
        DomainResult.Success(Unit)
    }
}
