package io.averkhogliad.ai.challenge.week6.infrastructure.db.repository

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexedSource
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.SourceType
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedSourceRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectSourcesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Path
import java.time.Instant

class SqlIndexedSourceRepository : IndexedSourceRepository {

    override suspend fun findByProjectId(projectId: String): List<IndexedSource> = transaction {
        ProjectSourcesTable.selectAll()
            .where { ProjectSourcesTable.projectId eq projectId }
            .map { row -> toSource(row) }
    }

    override suspend fun addSource(source: IndexedSource) {
        transaction {
            ProjectSourcesTable.insert {
                it[id] = source.id
                it[projectId] = source.projectId
                it[path] = source.path.toString()
                it[sourceType] = source.sourceType.name
                it[isDefault] = if (source.isDefault) 1 else 0
                it[createdAt] = source.createdAt.toEpochMilli()
            }
        }
    }

    override suspend fun removeSource(sourceId: String) {
        transaction {
            ProjectSourcesTable.deleteWhere { ProjectSourcesTable.id eq sourceId }
        }
    }

    override suspend fun removeByProjectId(projectId: String) {
        transaction {
            ProjectSourcesTable.deleteWhere { ProjectSourcesTable.projectId eq projectId }
        }
    }

    private fun toSource(row: ResultRow): IndexedSource = IndexedSource(
        id = row[ProjectSourcesTable.id],
        projectId = row[ProjectSourcesTable.projectId],
        path = Path.of(row[ProjectSourcesTable.path]),
        sourceType = SourceType.valueOf(row[ProjectSourcesTable.sourceType]),
        isDefault = row[ProjectSourcesTable.isDefault] == 1,
        createdAt = Instant.ofEpochMilli(row[ProjectSourcesTable.createdAt]),
    )
}
