package io.averkhogliad.ai.challenge.week6.infrastructure.db.repository

import io.averkhogliad.ai.challenge.week6.domain.review.*
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ReviewFindingsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ReviewsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class SqlReviewRepository : ReviewRepository {

    override suspend fun save(review: Review): Unit = transaction {
        ReviewsTable.insert {
            it[id] = review.id
            it[projectId] = review.projectId
            it[trigger] = review.trigger.name
            it[commitHash] = review.commitHash
            it[branch] = review.branch
            it[sourceBranch] = review.sourceBranch
            it[targetBranch] = review.targetBranch
            it[prId] = review.prId
            it[summary] = review.summary
            it[createdAt] = review.createdAt
        }

        review.findings.forEach { f ->
            ReviewFindingsTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[reviewId] = review.id
                it[category] = f.category.name
                it[severity] = f.severity.name
                it[file] = f.file
                it[line] = f.line
                it[description] = f.description
                it[recommendation] = f.recommendation
            }
        }
        Unit
    }

    override suspend fun findById(id: String): Review? = transaction {
        val reviewRow =
            ReviewsTable.selectAll().where { ReviewsTable.id eq id }.singleOrNull() ?: return@transaction null
        val findings = ReviewFindingsTable.selectAll()
            .where { ReviewFindingsTable.reviewId eq id }
            .map { row ->
                ReviewFinding(
                    category = FindingCategory.valueOf(row[ReviewFindingsTable.category]),
                    severity = Severity.valueOf(row[ReviewFindingsTable.severity]),
                    file = row[ReviewFindingsTable.file],
                    line = row[ReviewFindingsTable.line],
                    description = row[ReviewFindingsTable.description],
                    recommendation = row[ReviewFindingsTable.recommendation],
                )
            }

        Review(
            id = reviewRow[ReviewsTable.id],
            projectId = reviewRow[ReviewsTable.projectId],
            trigger = ReviewTrigger.valueOf(reviewRow[ReviewsTable.trigger]),
            commitHash = reviewRow[ReviewsTable.commitHash],
            branch = reviewRow[ReviewsTable.branch],
            sourceBranch = reviewRow[ReviewsTable.sourceBranch],
            targetBranch = reviewRow[ReviewsTable.targetBranch],
            prId = reviewRow[ReviewsTable.prId],
            summary = reviewRow[ReviewsTable.summary],
            findings = findings,
            createdAt = reviewRow[ReviewsTable.createdAt],
        )
    }

    override suspend fun findByProjectId(projectId: String, limit: Int): List<Review> = transaction {
        val reviewRows = ReviewsTable.selectAll()
            .where { ReviewsTable.projectId eq projectId }
            .orderBy(ReviewsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .toList()

        val reviewIds = reviewRows.map { it[ReviewsTable.id] }
        val allFindings = if (reviewIds.isNotEmpty()) {
            ReviewFindingsTable.selectAll()
                .where { ReviewFindingsTable.reviewId inList reviewIds }
                .map { row ->
                    row[ReviewFindingsTable.reviewId] to ReviewFinding(
                        category = FindingCategory.valueOf(row[ReviewFindingsTable.category]),
                        severity = Severity.valueOf(row[ReviewFindingsTable.severity]),
                        file = row[ReviewFindingsTable.file],
                        line = row[ReviewFindingsTable.line],
                        description = row[ReviewFindingsTable.description],
                        recommendation = row[ReviewFindingsTable.recommendation],
                    )
                }
                .groupBy({ it.first }, { it.second })
        } else {
            emptyMap()
        }

        reviewRows.map { reviewRow ->
            Review(
                id = reviewRow[ReviewsTable.id],
                projectId = reviewRow[ReviewsTable.projectId],
                trigger = ReviewTrigger.valueOf(reviewRow[ReviewsTable.trigger]),
                commitHash = reviewRow[ReviewsTable.commitHash],
                branch = reviewRow[ReviewsTable.branch],
                sourceBranch = reviewRow[ReviewsTable.sourceBranch],
                targetBranch = reviewRow[ReviewsTable.targetBranch],
                prId = reviewRow[ReviewsTable.prId],
                summary = reviewRow[ReviewsTable.summary],
                findings = allFindings[reviewRow[ReviewsTable.id]] ?: emptyList(),
                createdAt = reviewRow[ReviewsTable.createdAt],
            )
        }
    }

    override suspend fun findLatestByProjectId(projectId: String, limit: Int): List<Review> =
        findByProjectId(projectId, limit)
}
