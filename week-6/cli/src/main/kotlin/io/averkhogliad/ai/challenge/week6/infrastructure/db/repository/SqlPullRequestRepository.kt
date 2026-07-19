package io.averkhogliad.ai.challenge.week6.infrastructure.db.repository

import io.averkhogliad.ai.challenge.week6.domain.pr.PrStatus
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequest
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequestRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.PullRequestsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SqlPullRequestRepository : PullRequestRepository {

    override suspend fun save(pr: PullRequest): Unit = transaction {
        PullRequestsTable.insert {
            it[id] = pr.id
            it[projectId] = pr.projectId
            it[title] = pr.title
            it[sourceBranch] = pr.sourceBranch
            it[targetBranch] = pr.targetBranch
            it[status] = pr.status.name
            it[createdAt] = pr.createdAt
        }
        Unit
    }

    override suspend fun findById(id: String): PullRequest? = transaction {
        PullRequestsTable.selectAll()
            .where { PullRequestsTable.id eq id }
            .singleOrNull()
            ?.let { row ->
                PullRequest(
                    id = row[PullRequestsTable.id],
                    projectId = row[PullRequestsTable.projectId],
                    title = row[PullRequestsTable.title],
                    sourceBranch = row[PullRequestsTable.sourceBranch],
                    targetBranch = row[PullRequestsTable.targetBranch],
                    status = PrStatus.valueOf(row[PullRequestsTable.status]),
                    createdAt = row[PullRequestsTable.createdAt],
                )
            }
    }

    override suspend fun findByProjectId(projectId: String, statusFilter: PrStatus?): List<PullRequest> = transaction {
        val prs = PullRequestsTable.selectAll()
            .where { PullRequestsTable.projectId eq projectId }
            .orderBy(PullRequestsTable.createdAt, SortOrder.DESC)
            .map { row ->
                PullRequest(
                    id = row[PullRequestsTable.id],
                    projectId = row[PullRequestsTable.projectId],
                    title = row[PullRequestsTable.title],
                    sourceBranch = row[PullRequestsTable.sourceBranch],
                    targetBranch = row[PullRequestsTable.targetBranch],
                    status = PrStatus.valueOf(row[PullRequestsTable.status]),
                    createdAt = row[PullRequestsTable.createdAt],
                )
            }

        if (statusFilter != null) prs.filter { it.status == statusFilter } else prs
    }
}
