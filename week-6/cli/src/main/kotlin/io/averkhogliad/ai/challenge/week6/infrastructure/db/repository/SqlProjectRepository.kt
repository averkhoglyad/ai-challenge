package io.averkhogliad.ai.challenge.week6.infrastructure.db.repository

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.port.ProjectRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.nio.file.Path
import java.time.Instant

class SqlProjectRepository : ProjectRepository {

    override suspend fun save(project: Project): DomainResult<Project> = transaction {
        try {
            val existing = findByIdInternal(project.id)
            if (existing != null) {
                ProjectsTable.update({ ProjectsTable.id eq project.id }) {
                    it[name] = project.name
                    it[rootPath] = project.rootPath.toString()
                    it[docsPath] = project.docsPath?.toString()
                    it[faqPath] = project.faqPath?.toString()
                    it[updatedAt] = project.updatedAt.toEpochMilli()
                }
            } else {
                ProjectsTable.insert {
                    it[id] = project.id
                    it[name] = project.name
                    it[rootPath] = project.rootPath.toString()
                    it[docsPath] = project.docsPath?.toString()
                    it[faqPath] = project.faqPath?.toString()
                    it[createdAt] = project.createdAt.toEpochMilli()
                    it[updatedAt] = project.updatedAt.toEpochMilli()
                }
            }
            DomainResult.Success(project)
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findById(id: String): DomainResult<Project?> = transaction {
        try {
            DomainResult.Success(findByIdInternal(id))
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findAll(): DomainResult<List<Project>> = transaction {
        try {
            val rows = ProjectsTable.selectAll().toList()
            DomainResult.Success(rows.map(::toProject))
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findByPath(path: String): DomainResult<Project?> = transaction {
        try {
            val row = ProjectsTable.selectAll()
                .where { ProjectsTable.rootPath eq path }
                .singleOrNull()
            DomainResult.Success(row?.let(::toProject))
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun delete(id: String): DomainResult<Unit> = transaction {
        try {
            ProjectsTable.deleteWhere { ProjectsTable.id eq id }
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    private fun findByIdInternal(id: String): Project? =
        ProjectsTable.selectAll()
            .where { ProjectsTable.id eq id }
            .singleOrNull()
            ?.let(::toProject)

    private fun toProject(row: ResultRow): Project = Project(
        id = row[ProjectsTable.id],
        name = row[ProjectsTable.name],
        rootPath = Path.of(row[ProjectsTable.rootPath]),
        docsPath = row[ProjectsTable.docsPath]?.let { Path.of(it) },
        faqPath = row[ProjectsTable.faqPath]?.let { Path.of(it) },
        createdAt = Instant.ofEpochMilli(row[ProjectsTable.createdAt]),
        updatedAt = Instant.ofEpochMilli(row[ProjectsTable.updatedAt]),
    )
}
