package io.averkhogliad.ai.challenge.week6.infrastructure.release

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitInfo
import java.nio.file.Path
import java.time.Instant

class GitHistoryFetcher(
    private val gitPort: GitPort,
    private val conventionalCommitParser: ConventionalCommitParser = ConventionalCommitParser(),
    private val ticketIdExtractor: TicketIdExtractor = TicketIdExtractor(),
) {

    suspend fun fetch(
        rootPath: Path,
        range: String,
        limit: Int = DEFAULT_LIMIT,
        includeMergeCommits: Boolean = false,
    ): DomainResult<List<CommitInfo>> {
        require(limit in 1..MAX_LIMIT) { "limit must be between 1 and $MAX_LIMIT" }
        val (base, head) = parseRange(range) ?: return DomainResult.Failure(DomainError.gitRangeNotFound(range))

        return when (val result = gitPort.getCommitsBetween(rootPath, base, head, limit)) {
            is DomainResult.Failure -> DomainResult.Failure(result.error)
            is DomainResult.Success -> {
                val commits = parseCommits(result.value)
                    .filter { includeMergeCommits || !it.message.startsWith("Merge ") }
                if (commits.isEmpty()) {
                    DomainResult.Failure(DomainError.noCommitsInRange(range))
                } else {
                    DomainResult.Success(commits)
                }
            }
        }
    }

    private fun parseRange(range: String): Pair<String?, String>? {
        val normalized = range.trim()
        if (normalized.isEmpty()) return null
        if (".." !in normalized) return null to normalized
        val parts = normalized.split("..", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return parts[0] to parts[1]
    }

    private fun parseCommits(output: String): List<CommitInfo> = output
        .split(RECORD_SEPARATOR)
        .asSequence()
        .filter(String::isNotBlank)
        .mapNotNull(::parseCommit)
        .toList()

    private fun parseCommit(record: String): CommitInfo? {
        val fields = record.trim().split(FIELD_SEPARATOR, limit = 6)
        if (fields.size !in 5..6) return null

        val (hash, shortHash, author, date, message) = fields.take(5)
        val changedFiles = fields.getOrNull(5)
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toList()
            .orEmpty()
        return runCatching {
            CommitInfo(
                hash = hash,
                shortHash = shortHash,
                message = message.trimEnd(),
                author = author,
                date = Instant.parse(date),
                changedFiles = changedFiles,
                category = conventionalCommitParser.parse(message),
                ticketId = ticketIdExtractor.extract(message).firstOrNull(),
            )
        }.getOrNull()
    }

    private companion object {
        const val DEFAULT_LIMIT = 500
        const val MAX_LIMIT = 500
        const val FIELD_SEPARATOR = "\u001f"
        const val RECORD_SEPARATOR = "\u001e"
    }
}
