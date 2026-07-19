package io.averkhogliad.ai.challenge.week6.domain.pr

import kotlinx.serialization.Serializable

@Serializable
enum class PrStatus {
    OPEN,
    CLOSED,
    MERGED,
    DRAFT,
}

data class PullRequest(
    val id: String,
    val projectId: String,
    val title: String,
    val sourceBranch: String,
    val targetBranch: String,
    val status: PrStatus = PrStatus.OPEN,
    val createdAt: Long = java.time.Instant.now().toEpochMilli(),
)
