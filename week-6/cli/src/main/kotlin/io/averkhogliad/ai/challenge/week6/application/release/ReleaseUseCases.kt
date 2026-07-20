package io.averkhogliad.ai.challenge.week6.application.release

import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort
import io.averkhogliad.ai.challenge.week6.domain.release.model.*
import io.averkhogliad.ai.challenge.week6.domain.release.port.ReleaseRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.release.ChangelogFormatter
import io.averkhogliad.ai.challenge.week6.infrastructure.release.ConventionalCommitParser
import io.averkhogliad.ai.challenge.week6.infrastructure.release.GitHistoryFetcher
import io.averkhogliad.ai.challenge.week6.infrastructure.release.TicketIdExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.*

class ParseConventionalCommitUseCase(
    private val parser: ConventionalCommitParser,
) {
    fun execute(message: String): DomainResult<CommitCategory> = DomainResult.Success(parser.parse(message))
}

class ExtractTicketIdUseCase(
    private val extractor: TicketIdExtractor,
) {
    fun execute(message: String): List<String> = extractor.extract(message)
}

class ClassifyCommitUseCase(
    private val llmClient: LlmClient,
) {
    suspend fun execute(message: String): DomainResult<CommitCategory> = try {
        val response = llmClient.chat(
            prompt = """Classify this commit message. Reply with exactly one category: FEATURE, FIX, BREAKING, DOCS, REFACTOR, PERFORMANCE, CHORE, UNKNOWN.

Commit message:
$message""",
        )
        DomainResult.Success(parseCategory(response.content))
    } catch (_: Exception) {
        DomainResult.Success(CommitCategory.UNKNOWN)
    }

    private fun parseCategory(content: String?): CommitCategory = content
        ?.trim()
        ?.uppercase()
        ?.lineSequence()
        ?.firstOrNull()
        ?.let { value -> CommitCategory.entries.firstOrNull { it.name == value } }
        ?: CommitCategory.UNKNOWN
}

class HybridCommitClassifier(
    private val parseUseCase: ParseConventionalCommitUseCase,
    private val classifyUseCase: ClassifyCommitUseCase,
) {
    suspend fun classify(message: String): DomainResult<CommitCategory> {
        val parsed = parseUseCase.execute(message)
        if (parsed is DomainResult.Failure) return parsed
        val category = (parsed as DomainResult.Success).value
        return if (category == CommitCategory.UNKNOWN) classifyUseCase.execute(message) else DomainResult.Success(
            category
        )
    }
}

class SuggestVersionUseCase(
    private val releaseRepository: ReleaseRepository,
) {
    suspend fun execute(projectId: String, commits: List<CommitInfo>): DomainResult<VersionSuggestion> {
        if (commits.isEmpty()) return DomainResult.Failure(DomainError.noCommitsInRange("selected range"))
        val latest = when (val result = releaseRepository.findLatestByProjectId(projectId)) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        val bump = when {
            commits.any { it.category == CommitCategory.BREAKING } -> VersionBump.MAJOR
            commits.any { it.category == CommitCategory.FEATURE } -> VersionBump.MINOR
            else -> VersionBump.PATCH
        }
        val suggested = nextVersion(latest?.version, bump)
            ?: return DomainResult.Failure(DomainError.invalidVersionFormat(latest?.version.orEmpty()))
        val count = commits.count { it.category == CommitCategory.BREAKING }
        val reasoning = when (bump) {
            VersionBump.MAJOR -> "$count breaking change(s) detected"
            VersionBump.MINOR -> "new feature(s) detected without breaking changes"
            VersionBump.PATCH -> "no features or breaking changes detected"
        }
        return DomainResult.Success(VersionSuggestion(latest?.version, suggested, bump, reasoning))
    }

    private fun nextVersion(current: String?, bump: VersionBump): String? {
        if (current == null) return "v0.1.0"
        val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(current) ?: return null
        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        val patch = match.groupValues[3].toInt()
        return when (bump) {
            VersionBump.MAJOR -> "v${major + 1}.0.0"
            VersionBump.MINOR -> "v$major.${minor + 1}.0"
            VersionBump.PATCH -> "v$major.$minor.${patch + 1}"
        }
    }
}

class EnhanceWithRagContextUseCase(
    private val ragService: RagService?,
) {
    suspend fun execute(): ReleaseContext = try {
        val entries = ragService?.search("similar changes in previous releases", topK = 3).orEmpty()
        ReleaseContext(entries.map { it.text }, warning = null)
    } catch (_: Exception) {
        ReleaseContext(emptyList(), warning = "RAG context unavailable; generated changelog uses git history only")
    }
}

data class ReleaseContext(val entries: List<String>, val warning: String?)

class ListReleasesUseCase(
    private val releaseRepository: ReleaseRepository,
) {
    suspend fun execute(projectId: String, limit: Int = 10): DomainResult<List<Release>> {
        if (limit !in 1..100) return DomainResult.Failure(DomainError.repository("limit must be between 1 and 100"))
        return releaseRepository.findByProjectId(projectId, limit)
    }
}

class ShowReleaseUseCase(
    private val releaseRepository: ReleaseRepository,
) {
    suspend fun execute(projectId: String, version: String): DomainResult<Release> =
        when (val result = releaseRepository.findByProjectIdAndVersion(projectId, version)) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> result.value?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(DomainError.releaseNotFound(version))
        }
}

sealed interface ReleaseProgress {
    data object Started : ReleaseProgress
    data class Analyzing(val range: String) : ReleaseProgress
    data class Classifying(val processed: Int, val total: Int) : ReleaseProgress
    data object Generating : ReleaseProgress
    data class PreviewReady(val draft: ReleaseDraft) : ReleaseProgress
    data class Warning(val message: String) : ReleaseProgress
    data class Error(val error: DomainError) : ReleaseProgress
}

data class ReleaseRequest(
    val projectId: String,
    val rootPath: java.nio.file.Path,
    val version: String?,
    val range: String?
)

data class ReleaseDraft(val release: Release, val markdown: String)

class GenerateReleaseNotesUseCase(
    private val gitHistoryFetcher: GitHistoryFetcher,
    private val commitClassifier: HybridCommitClassifier,
    private val suggestVersionUseCase: SuggestVersionUseCase,
    private val enhanceWithRagUseCase: EnhanceWithRagContextUseCase,
    private val changelogFormatter: ChangelogFormatter,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun execute(request: ReleaseRequest): Flow<ReleaseProgress> = flow {
        emit(ReleaseProgress.Started)
        val range = request.range ?: "HEAD"
        emit(ReleaseProgress.Analyzing(range))
        val fetched = gitHistoryFetcher.fetch(request.rootPath, range)
        val commits = when (fetched) {
            is DomainResult.Failure -> {
                emit(ReleaseProgress.Error(fetched.error)); return@flow
            }

            is DomainResult.Success -> fetched.value
        }
        val classified = commits.mapIndexed { index, commit ->
            emit(ReleaseProgress.Classifying(index + 1, commits.size))
            val category = commitClassifier.classify(commit.message).getOrNull() ?: CommitCategory.UNKNOWN
            commit.copy(category = category)
        }
        val suggestion = when (val result = suggestVersionUseCase.execute(request.projectId, classified)) {
            is DomainResult.Failure -> {
                emit(ReleaseProgress.Error(result.error)); return@flow
            }

            is DomainResult.Success -> result.value
        }
        val version = request.version ?: suggestion.suggestedVersion
        if (!VERSION_REGEX.matches(version)) {
            emit(ReleaseProgress.Error(DomainError.invalidVersionFormat(version))); return@flow
        }
        emit(ReleaseProgress.Generating)
        val context = enhanceWithRagUseCase.execute()
        context.warning?.let { emit(ReleaseProgress.Warning(it)) }
        val changelog = buildChangelog(version, classified, clock.instant(), context)
        val release = Release(
            id = "rel-${UUID.randomUUID()}", projectId = request.projectId, version = version,
            previousVersion = suggestion.currentVersion, range = range, commits = classified,
            changelog = changelog, createdAt = clock.instant(),
        )
        emit(ReleaseProgress.PreviewReady(ReleaseDraft(release, changelogFormatter.format(changelog))))
    }

    private fun buildChangelog(
        version: String,
        commits: List<CommitInfo>,
        now: Instant,
        context: ReleaseContext
    ): Changelog {
        val sections = SECTION_TITLES.mapNotNull { (category, title) ->
            val grouped = commits.filter { it.category == category }
            if (grouped.isEmpty()) null else ChangelogSection(title, grouped.map { commit ->
                ChangelogEntry(
                    commit.message.lineSequence().first(),
                    listOf(commit.shortHash),
                    listOfNotNull(commit.ticketId),
                    category == CommitCategory.BREAKING
                )
            })
        }
        val summary =
            "${commits.size} change(s) included" + if (context.entries.isEmpty()) "" else "; historical context consulted"
        return Changelog(version, LocalDate.ofInstant(now, clock.zone), sections, summary)
    }

    private companion object {
        val VERSION_REGEX = Regex("^v?\\d+\\.\\d+\\.\\d+$")
        val SECTION_TITLES = linkedMapOf(
            CommitCategory.BREAKING to "Breaking Changes",
            CommitCategory.FEATURE to "Features",
            CommitCategory.FIX to "Bug Fixes",
            CommitCategory.PERFORMANCE to "Performance",
            CommitCategory.REFACTOR to "Refactoring",
            CommitCategory.DOCS to "Documentation",
            CommitCategory.CHORE to "Maintenance",
            CommitCategory.UNKNOWN to "Other Changes",
        )
    }
}

class ConfirmReleaseUseCase(
    private val fileOpsPort: FileOpsPort,
    private val releaseRepository: ReleaseRepository,
    private val changelogPath: RelativePath,
) {
    suspend fun execute(draft: ReleaseDraft): DomainResult<Unit> {
        val existingContent = when (val existing = fileOpsPort.read(changelogPath)) {
            is DomainResult.Success -> {
                if (existing.value.truncated) {
                    return DomainResult.Failure(
                        DomainError.repository("CHANGELOG.md is too large to preserve existing history")
                    )
                }
                existing.value.content
            }

            is DomainResult.Failure -> when (existing.error) {
                is DomainError.FileNotFound -> ""
                else -> return existing
            }
        }
        val changelog =
            listOf(draft.markdown.trimEnd(), existingContent.trim()).filter(String::isNotEmpty).joinToString("\n\n")
        val written = fileOpsPort.write(changelogPath, changelog)
        if (written is DomainResult.Failure) return written
        return when (val saved = releaseRepository.save(draft.release)) {
            is DomainResult.Success -> saved
            is DomainResult.Failure -> DomainResult.Failure(
                DomainError.releasePersistencePartialFailure(saved.error.message)
            )
        }
    }
}
