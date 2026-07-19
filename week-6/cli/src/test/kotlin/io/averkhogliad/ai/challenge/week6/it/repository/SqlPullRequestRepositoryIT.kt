package io.averkhogliad.ai.challenge.week6.it.repository

import io.averkhogliad.ai.challenge.week6.domain.pr.PrStatus
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequest
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlPullRequestRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.PullRequestsTable
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class SqlPullRequestRepositoryIT : FreeSpec({

    lateinit var dbPath: Path

    fun setupDb(): Database {
        dbPath = Files.createTempFile("test-pr-", ".db")
        val db = Database.connect("jdbc:sqlite:${dbPath.toAbsolutePath()}", "org.sqlite.JDBC")
        transaction(db) { SchemaUtils.create(ProjectsTable, PullRequestsTable) }
        return db
    }

    afterEach {
        Files.deleteIfExists(dbPath)
    }

    "save and findById" - {

        "saves PR and retrieves it" {
            runTest {
                setupDb()
                val repository = SqlPullRequestRepository()
                val pr = PullRequest(
                    id = UUID.randomUUID().toString(),
                    projectId = "proj-pr1",
                    title = "Feature X",
                    sourceBranch = "feature/x",
                    targetBranch = "main",
                    status = PrStatus.OPEN,
                )

                repository.save(pr)
                val result = repository.findById(pr.id)

                result shouldNotBe null
                result!!.id shouldBe pr.id
                result.projectId shouldBe "proj-pr1"
                result.title shouldBe "Feature X"
                result.sourceBranch shouldBe "feature/x"
                result.targetBranch shouldBe "main"
                result.status shouldBe PrStatus.OPEN
            }
        }

        "returns null for unknown id" {
            runTest {
                setupDb()
                val repository = SqlPullRequestRepository()
                repository.findById("nonexistent-pr") shouldBe null
            }
        }
    }

    "findByProjectId" - {

        "returns PRs in descending order by createdAt" {
            runTest {
                setupDb()
                val repository = SqlPullRequestRepository()
                val projectId = "proj-pr-list"
                val pr1 = PullRequest(
                    id = UUID.randomUUID().toString(), projectId = projectId, title = "First PR",
                    sourceBranch = "f1", targetBranch = "main", createdAt = 1000L,
                )
                val pr2 = PullRequest(
                    id = UUID.randomUUID().toString(), projectId = projectId, title = "Second PR",
                    sourceBranch = "f2", targetBranch = "develop", createdAt = 2000L,
                )
                repository.save(pr1)
                repository.save(pr2)

                val result = repository.findByProjectId(projectId)
                result shouldHaveSize 2
                result[0].id shouldBe pr2.id
                result[1].id shouldBe pr1.id
            }
        }

        "filters by status when provided" {
            runTest {
                setupDb()
                val repository = SqlPullRequestRepository()
                val projectId = "proj-status-filter"
                repository.save(
                    PullRequest(
                        id = UUID.randomUUID().toString(), projectId = projectId, title = "Open PR",
                        sourceBranch = "f1", targetBranch = "main", status = PrStatus.OPEN,
                    )
                )
                repository.save(
                    PullRequest(
                        id = UUID.randomUUID().toString(), projectId = projectId, title = "Closed PR",
                        sourceBranch = "f2", targetBranch = "main", status = PrStatus.CLOSED,
                    )
                )

                val openResults = repository.findByProjectId(projectId, PrStatus.OPEN)
                val closedResults = repository.findByProjectId(projectId, PrStatus.CLOSED)
                openResults shouldHaveSize 1
                closedResults shouldHaveSize 1
            }
        }

        "returns empty list for unknown project" {
            runTest {
                setupDb()
                val repository = SqlPullRequestRepository()
                repository.findByProjectId("unknown-project") shouldHaveSize 0
            }
        }
    }
})
