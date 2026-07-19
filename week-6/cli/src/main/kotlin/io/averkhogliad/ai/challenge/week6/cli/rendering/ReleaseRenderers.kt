package io.averkhogliad.ai.challenge.week6.cli.rendering

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitCategory
import io.averkhogliad.ai.challenge.week6.domain.release.model.Release
import io.averkhogliad.cli.repl.core.Renderer
import io.averkhogliad.cli.repl.mordant.common.MarkdownRenderer
import io.averkhogliad.cli.repl.mordant.common.TableRenderer

class ReleaseTableRenderer(
    terminal: Terminal,
) : TableRenderer<List<Release>>(terminal) {

    override fun headers(): List<String> = listOf("Version", "Date", "Commits", "Breaking Changes")

    override fun rows(data: List<Release>): List<List<String>> = data.map { release ->
        val breakingChanges = release.commits.count { it.category == CommitCategory.BREAKING }
        listOf(
            coloredVersion(release, breakingChanges),
            release.changelog.date.toString(),
            release.commits.size.toString(),
            breakingChanges.toString(),
        )
    }

    override fun render(data: List<Release>): String =
        if (data.isEmpty()) "No releases found." else super.render(data)

    private fun coloredVersion(release: Release, breakingChanges: Int): String = when {
        breakingChanges > 0 -> TextColors.red(release.version)
        release.commits.any { it.category == CommitCategory.FEATURE } -> TextColors.yellow(release.version)
        else -> TextColors.green(release.version)
    }
}

class ReleaseDetailRenderer(
    private val markdownRenderer: MarkdownRenderer,
) : Renderer<Release> {

    override fun render(data: Release): String = markdownRenderer.render(toMarkdown(data))

    private fun toMarkdown(release: Release): String = buildString {
        appendLine("# ${release.version} (${release.changelog.date})")
        appendLine()
        if (release.changelog.summary.isNotBlank()) {
            appendLine(release.changelog.summary)
            appendLine()
        }
        release.changelog.sections.forEach { section ->
            appendLine("## ${section.title}")
            section.entries.forEach { entry ->
                val commits = entry.commits.joinToString(separator = ", ") { "($it)" }
                val tickets = entry.ticketIds.joinToString(separator = " ") { "($it)" }
                append("- ${entry.description}")
                if (commits.isNotBlank()) append(" $commits")
                if (tickets.isNotBlank()) append(" $tickets")
                appendLine()
            }
            appendLine()
        }
    }
}
