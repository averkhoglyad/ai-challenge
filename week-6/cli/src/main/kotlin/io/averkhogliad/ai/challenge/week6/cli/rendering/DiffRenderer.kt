package io.averkhogliad.ai.challenge.week6.cli.rendering

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal

class DiffRenderer(
    private val terminal: Terminal,
) {
    fun render(diff: String): String {
        val sb = StringBuilder()
        val lines = diff.lines()

        var currentFile: String? = null

        for (line in lines) {
            when {
                line.startsWith("diff --git") -> {
                    currentFile = line.substringAfter(" b/")
                    sb.appendLine(terminal.render(TextColors.cyan(line)))
                }

                line.startsWith("+++") || line.startsWith("---") -> {
                    sb.appendLine(terminal.render(TextColors.yellow(line)))
                }

                line.startsWith("+") && !line.startsWith("+++") -> {
                    sb.appendLine(terminal.render(TextColors.green(line)))
                }

                line.startsWith("-") && !line.startsWith("---") -> {
                    sb.appendLine(terminal.render(TextColors.red(line)))
                }

                line.startsWith("@@") -> {
                    sb.appendLine(terminal.render(TextColors.magenta(line)))
                }

                else -> {
                    sb.appendLine(line)
                }
            }
        }

        return sb.toString()
    }

    fun renderSummary(diff: String): String {
        val lines = diff.lines()
        val filesChanged = lines.filter { it.startsWith("diff --git") }.size
        val additions = lines.count { it.startsWith("+") && !it.startsWith("+++") }
        val deletions = lines.count { it.startsWith("-") && !it.startsWith("---") }
        val totalLines = lines.size

        return buildString {
            appendLine(terminal.render(TextStyles.bold("Diff Summary")))
            appendLine("Files changed: $filesChanged")
            appendLine("Additions: ${TextColors.green("+$additions")}")
            appendLine("Deletions: ${TextColors.red("-$deletions")}")
            appendLine("Total lines: $totalLines")
        }
    }
}
