package io.averkhogliad.ai.challenge.week6.domain.pr

interface PullRequestRepository {
    suspend fun save(pr: PullRequest)
    suspend fun findById(id: String): PullRequest?
    suspend fun findByProjectId(projectId: String, statusFilter: PrStatus? = null): List<PullRequest>
}
