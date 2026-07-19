package io.averkhogliad.ai.challenge.week6.domain.review

import kotlinx.serialization.Serializable

@Serializable
enum class ReviewTrigger {
    AUTO,
    MANUAL,
    PR,
}

@Serializable
enum class FindingCategory {
    BUG,
    ARCHITECTURE,
    PERFORMANCE,
    SECURITY,
    BEST_PRACTICE,
    READABILITY,
    MAINTAINABILITY,
    OTHER,
}

@Serializable
enum class Severity {
    CRITICAL,
    WARNING,
    INFO,
}

@Serializable
data class ReviewFinding(
    val category: FindingCategory,
    val severity: Severity,
    val file: String? = null,
    val line: Int? = null,
    val description: String,
    val recommendation: String? = null,
)

data class Review(
    val id: String,
    val projectId: String,
    val trigger: ReviewTrigger,
    val commitHash: String? = null,
    val branch: String? = null,
    val sourceBranch: String? = null,
    val targetBranch: String? = null,
    val prId: String? = null,
    val diff: String? = null,
    val summary: String? = null,
    val findings: List<ReviewFinding> = emptyList(),
    val createdAt: Long = java.time.Instant.now().toEpochMilli(),
)
