package io.averkhogliad.ai.challenge.week6.infrastructure.release

import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitCategory

class ConventionalCommitParser {

    fun parse(message: String): CommitCategory {
        if (message.contains(BREAKING_CHANGE_FOOTER, ignoreCase = true)) {
            return CommitCategory.BREAKING
        }

        val type = conventionalCommitPattern.find(message.lineSequence().firstOrNull().orEmpty())
            ?.groupValues
            ?.get(1)
            ?.lowercase()
            ?: return CommitCategory.UNKNOWN

        return typeToCategory[type] ?: CommitCategory.UNKNOWN
    }

    private companion object {
        const val BREAKING_CHANGE_FOOTER = "BREAKING CHANGE:"
        val conventionalCommitPattern = Regex("^([A-Za-z]+)(?:\\([^)]+\\))?!?:\\s+.+")
        val typeToCategory = mapOf(
            "feat" to CommitCategory.FEATURE,
            "add" to CommitCategory.FEATURE,
            "new" to CommitCategory.FEATURE,
            "fix" to CommitCategory.FIX,
            "bugfix" to CommitCategory.FIX,
            "breaking" to CommitCategory.BREAKING,
            "docs" to CommitCategory.DOCS,
            "documentation" to CommitCategory.DOCS,
            "refactor" to CommitCategory.REFACTOR,
            "perf" to CommitCategory.PERFORMANCE,
            "performance" to CommitCategory.PERFORMANCE,
            "chore" to CommitCategory.CHORE,
            "maintenance" to CommitCategory.CHORE,
        )
    }
}
