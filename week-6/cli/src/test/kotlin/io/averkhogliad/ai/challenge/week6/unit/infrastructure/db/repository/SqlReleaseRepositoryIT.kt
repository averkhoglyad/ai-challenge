package io.averkhogliad.ai.challenge.week6.unit.infrastructure.db.repository

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.release.model.Changelog
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitCategory
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitInfo
import io.averkhogliad.ai.challenge.week6.domain.release.model.Release
import io.averkhogliad.ai.challenge.week6.infrastructure.db.DatabaseFactory
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlProjectRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlReleaseRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ReleasesTable
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate

class SqlReleaseRepositoryIT : FreeSpec({
    lateinit var dbFile: java.nio.file.Path
    lateinit var repository: SqlReleaseRepository

    beforeEach {
        dbFile = Files.createTempFile("week6-release-", ".db")
        val db = DatabaseFactory.connect(dbFile)
        transaction(db) { SchemaUtils.create(ProjectsTable, ReleasesTable) }
        repository = SqlReleaseRepository()
        val now = Instant.now()
        val project = Project(
            id = "project-1",
            name = "Test",
            rootPath = Files.createTempDirectory("project-root-"),
            docsPath = null,
            faqPath = null,
            createdAt = now,
            updatedAt = now,
        )
        SqlProjectRepository().save(project)
    }

    afterEach {
        Files.deleteIfExists(dbFile)
    }

    "DatabaseFactory" - {
        "enforces foreign keys on Exposed transaction connections" {
            runTest {
                // given
                val orphanRelease =
                    testRelease("orphan-release", "v9.9.9", Instant.EPOCH).copy(projectId = "missing-project")

                // when
                val result = repository.save(orphanRelease)

                // then
                result.isFailure shouldBe true
            }
        }
    }

    "SqlReleaseRepository" - {
        "upserts a release by project and version so version lookup stays deterministic" {
            runTest {
                // given
                val first = testRelease("release-1", "v1.0.0", Instant.parse("2026-01-01T00:00:00Z"))
                val replacement = testRelease("release-2", "v1.0.0", Instant.parse("2026-02-01T00:00:00Z"))

                // when
                repository.save(first).isSuccess shouldBe true
                repository.save(replacement).isSuccess shouldBe true
                val releases = repository.findByProjectId("project-1")
                val found = repository.findByProjectIdAndVersion("project-1", "v1.0.0")

                // then
                (releases as DomainResult.Success).value.size shouldBe 1
                ((found as DomainResult.Success).value?.createdAt) shouldBe replacement.createdAt
            }
        }

        "saves finds updates and deletes releases" {
            runTest {
                // given
                val older = testRelease("release-1", "v1.0.0", Instant.parse("2026-01-01T00:00:00Z"))
                val newer = testRelease("release-2", "v1.1.0", Instant.parse("2026-02-01T00:00:00Z"))

                // when
                repository.save(older).isSuccess shouldBe true
                repository.save(newer).isSuccess shouldBe true
                val releases = repository.findByProjectId("project-1")
                val latest = repository.findLatestByProjectId("project-1")
                val found = repository.findById("release-1")
                val foundByVersion = repository.findByProjectIdAndVersion("project-1", "v1.0.0")
                repository.delete("release-1").isSuccess shouldBe true

                // then
                (releases as DomainResult.Success).value.map { it.id } shouldBe listOf("release-2", "release-1")
                ((latest as DomainResult.Success).value?.id) shouldBe "release-2"
                ((found as DomainResult.Success).value?.changelog?.summary) shouldBe "summary"
                ((foundByVersion as DomainResult.Success).value?.id) shouldBe "release-1"
                ((repository.findById("release-1") as DomainResult.Success).value) shouldBe null
            }
        }
    }
}) {
    companion object {
        private fun testRelease(id: String, version: String, createdAt: Instant) = Release(
            id = id,
            projectId = "project-1",
            version = version,
            previousVersion = null,
            range = "HEAD~1..HEAD",
            commits = listOf(
                CommitInfo(
                    "abcdef123456",
                    "abcdef1",
                    "feat: feature",
                    "Ada",
                    createdAt,
                    emptyList(),
                    CommitCategory.FEATURE,
                    "#42"
                )
            ),
            changelog = Changelog(version, LocalDate.of(2026, 1, 1), emptyList(), "summary"),
            createdAt = createdAt,
        )
    }
}
