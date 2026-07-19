package io.averkhogliad.ai.challenge.week6.infrastructure.db.repository

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.release.model.Changelog
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitInfo
import io.averkhogliad.ai.challenge.week6.domain.release.model.Release
import io.averkhogliad.ai.challenge.week6.domain.release.port.ReleaseRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ReleasesTable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class SqlReleaseRepository(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : ReleaseRepository {

    override suspend fun save(release: Release): DomainResult<Unit> = transaction {
        try {
            val changelogJson = json.encodeToString(Changelog.serializer(), release.changelog)
            val commitsJson = json.encodeToString(ListSerializer(CommitInfo.serializer()), release.commits)

            val existing = ReleasesTable.selectAll()
                .where { (ReleasesTable.projectId eq release.projectId) and (ReleasesTable.version eq release.version) }
                .singleOrNull()

            if (existing == null) {
                ReleasesTable.insert {
                    it[id] = release.id
                    it[projectId] = release.projectId
                    it[version] = release.version
                    it[previousVersion] = release.previousVersion
                    it[range] = release.range
                    it[ReleasesTable.changelogJson] = changelogJson
                    it[ReleasesTable.commitsJson] = commitsJson
                    it[createdAt] = release.createdAt.toEpochMilli()
                }
            } else {
                ReleasesTable.update({ ReleasesTable.id eq existing[ReleasesTable.id] }) {
                    it[projectId] = release.projectId
                    it[version] = release.version
                    it[previousVersion] = release.previousVersion
                    it[range] = release.range
                    it[ReleasesTable.changelogJson] = changelogJson
                    it[ReleasesTable.commitsJson] = commitsJson
                    it[createdAt] = release.createdAt.toEpochMilli()
                }
            }
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findById(id: String): DomainResult<Release?> = transaction {
        try {
            DomainResult.Success(findByIdInternal(id))
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findByProjectIdAndVersion(projectId: String, version: String): DomainResult<Release?> =
        transaction {
            try {
                val release = ReleasesTable.selectAll()
                    .where { (ReleasesTable.projectId eq projectId) and (ReleasesTable.version eq version) }
                    .singleOrNull()
                    ?.let(::toRelease)
                DomainResult.Success(release)
            } catch (e: Exception) {
                DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
            }
        }

    override suspend fun findByProjectId(projectId: String, limit: Int): DomainResult<List<Release>> = transaction {
        try {
            require(limit > 0) { "limit must be greater than zero" }
            val releases = ReleasesTable.selectAll()
                .where { ReleasesTable.projectId eq projectId }
                .orderBy(ReleasesTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .map(::toRelease)
            DomainResult.Success(releases)
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findLatestByProjectId(projectId: String): DomainResult<Release?> = transaction {
        try {
            val release = ReleasesTable.selectAll()
                .where { ReleasesTable.projectId eq projectId }
                .orderBy(ReleasesTable.createdAt, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let(::toRelease)
            DomainResult.Success(release)
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun delete(id: String): DomainResult<Unit> = transaction {
        try {
            ReleasesTable.deleteWhere { ReleasesTable.id eq id }
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    private fun findByIdInternal(id: String): Release? =
        ReleasesTable.selectAll()
            .where { ReleasesTable.id eq id }
            .singleOrNull()
            ?.let(::toRelease)

    private fun toRelease(row: ResultRow): Release = Release(
        id = row[ReleasesTable.id],
        projectId = row[ReleasesTable.projectId],
        version = row[ReleasesTable.version],
        previousVersion = row[ReleasesTable.previousVersion],
        range = row[ReleasesTable.range],
        changelog = json.decodeFromString(Changelog.serializer(), row[ReleasesTable.changelogJson]),
        commits = json.decodeFromString(ListSerializer(CommitInfo.serializer()), row[ReleasesTable.commitsJson]),
        createdAt = java.time.Instant.ofEpochMilli(row[ReleasesTable.createdAt]),
    )
}
