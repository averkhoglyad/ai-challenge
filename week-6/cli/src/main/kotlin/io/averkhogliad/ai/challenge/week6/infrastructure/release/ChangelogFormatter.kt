package io.averkhogliad.ai.challenge.week6.infrastructure.release

import io.averkhogliad.ai.challenge.week6.domain.release.model.Changelog

class ChangelogFormatter {
    fun format(changelog: Changelog): String = buildString {
        appendLine("# ${changelog.version} (${changelog.date})")
        appendLine()
        if (changelog.summary.isNotBlank()) appendLine(changelog.summary)
        changelog.sections.forEach { section ->
            appendLine()
            appendLine("## ${section.title}")
            section.entries.forEach { entry ->
                append("- ${entry.description}")
                val references = entry.commits.map { "($it)" } + entry.ticketIds.map { "($it)" }
                if (references.isNotEmpty()) append(" ${references.joinToString(" ")}")
                appendLine()
            }
        }
    }.trimEnd() + "\n"
}
