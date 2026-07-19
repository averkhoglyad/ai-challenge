package io.averkhogliad.ai.challenge.week6.domain.release.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
data class Release(
    val id: String,
    val projectId: String,
    val version: String,
    val previousVersion: String?,
    val range: String,
    val commits: List<CommitInfo>,
    val changelog: Changelog,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant,
)

@Serializable
data class CommitInfo(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    @Serializable(with = InstantAsStringSerializer::class)
    val date: Instant,
    val changedFiles: List<String>,
    val category: CommitCategory,
    val ticketId: String?,
)

@Serializable
enum class CommitCategory {
    FEATURE,
    FIX,
    BREAKING,
    DOCS,
    REFACTOR,
    PERFORMANCE,
    CHORE,
    UNKNOWN,
}

@Serializable
data class Changelog(
    val version: String,
    @Serializable(with = LocalDateAsStringSerializer::class)
    val date: LocalDate,
    val sections: List<ChangelogSection>,
    val summary: String,
)

@Serializable
data class ChangelogSection(
    val title: String,
    val entries: List<ChangelogEntry>,
)

@Serializable
data class ChangelogEntry(
    val description: String,
    val commits: List<String>,
    val ticketIds: List<String>,
    val breakingChange: Boolean,
)

@Serializable
data class VersionSuggestion(
    val currentVersion: String?,
    val suggestedVersion: String,
    val bumpType: VersionBump,
    val reasoning: String,
)

@Serializable
enum class VersionBump {
    MAJOR,
    MINOR,
    PATCH,
}
