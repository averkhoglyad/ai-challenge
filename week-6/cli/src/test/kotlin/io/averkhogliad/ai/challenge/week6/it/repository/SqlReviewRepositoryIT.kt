package io.averkhogliad.ai.challenge.week6.it.repository

import io.averkhogliad.ai.challenge.week6.domain.review.*
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlReviewRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ReviewFindingsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ReviewsTable
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

class SqlReviewRepositoryIT : FreeSpec({

    lateinit var dbPath: Path

    fun setupDb(): Database {
        dbPath = Files.createTempFile("test-review-", ".db")
        val db = Database.connect("jdbc:sqlite:${dbPath.toAbsolutePath()}", "org.sqlite.JDBC")
        transaction(db) { SchemaUtils.create(ProjectsTable, ReviewsTable, ReviewFindingsTable) }
        return db
    }

    afterEach {
        Files.deleteIfExists(dbPath)
    }

    "save and findById" - {

        "saves review with findings and retrieves it" {
            runTest {
                setupDb()
                val repository = SqlReviewRepository()
                val review = Review(
                    id = UUID.randomUUID().toString(),
                    projectId = "proj-r1",
                    trigger = ReviewTrigger.MANUAL,
                    commitHash = "abc12345",
                    branch = "main",
                    summary = "All good",
                    findings = listOf(
                        ReviewFinding(
                            category = FindingCategory.BUG,
                            severity = Severity.CRITICAL,
                            file = "src/App.kt",
                            line = 42,
                            description = "NullPointerException possible",
                            recommendation = "Add null check",
                        ),
                        ReviewFinding(
                            category = FindingCategory.BEST_PRACTICE,
                            severity = Severity.WARNING,
                            file = "src/Util.kt",
                            line = 10,
                            description = "Use val instead of var",
                            recommendation = null,
                        ),
                    ),
                )

                repository.save(review)
                val result = repository.findById(review.id)

                result shouldNotBe null
                result!!.id shouldBe review.id
                result.projectId shouldBe review.projectId
                result.trigger shouldBe ReviewTrigger.MANUAL
                result.commitHash shouldBe "abc12345"
                result.summary shouldBe "All good"
                result.findings shouldHaveSize 2
                result.findings[0].category shouldBe FindingCategory.BUG
                result.findings[0].severity shouldBe Severity.CRITICAL
                result.findings[1].recommendation shouldBe null
            }
        }

        "returns null for unknown id" {
            runTest {
                setupDb()
                val repository = SqlReviewRepository()
                repository.findById("nonexistent") shouldBe null
            }
        }

        "saves review with empty findings" {
            runTest {
                setupDb()
                val repository = SqlReviewRepository()
                val review = Review(
                    id = UUID.randomUUID().toString(),
                    projectId = "proj-empty",
                    trigger = ReviewTrigger.AUTO,
                    summary = "No issues",
                    findings = emptyList(),
                )
                repository.save(review)
                val result = repository.findById(review.id)
                result shouldNotBe null
                result!!.findings shouldHaveSize 0
            }
        }
    }

    "findByProjectId" - {

        "returns reviews in descending order" {
            runTest {
                setupDb()
                val repository = SqlReviewRepository()
                val projectId = "proj-sorted"
                val r1 = Review(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    trigger = ReviewTrigger.AUTO,
                    createdAt = 1000L
                )
                val r2 = Review(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    trigger = ReviewTrigger.MANUAL,
                    createdAt = 2000L
                )
                val r3 = Review(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    trigger = ReviewTrigger.PR,
                    createdAt = 3000L
                )
                repository.save(r1)
                repository.save(r2)
                repository.save(r3)

                val result = repository.findByProjectId(projectId)
                result shouldHaveSize 3
                result[0].id shouldBe r3.id
                result[1].id shouldBe r2.id
                result[2].id shouldBe r1.id
            }
        }

        "returns empty list for unknown project" {
            runTest {
                setupDb()
                val repository = SqlReviewRepository()
                repository.findByProjectId("unknown-project") shouldHaveSize 0
            }
        }
    }

    "findLatestByProjectId" - {

        "respects limit parameter" {
            runTest {
                setupDb()
                val repository = SqlReviewRepository()
                val projectId = "proj-limit"
                repeat(5) { i ->
                    repository.save(
                        Review(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            trigger = ReviewTrigger.AUTO,
                            createdAt = (i * 1000).toLong(),
                        )
                    )
                }

                val result = repository.findLatestByProjectId(projectId, 3)
                result shouldHaveSize 3
                result[0].createdAt shouldBe 4000L
                result[1].createdAt shouldBe 3000L
            }
        }
    }
})
