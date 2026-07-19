package io.averkhogliad.ai.challenge.week6.cli.rendering

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.Diff
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.DiffLine
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.WordDiff

class DiffRenderer(
    private val terminal: Terminal,
) {
    companion object {
        private const val MAX_DIFF_LINES = 200
    }

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

    fun renderDiffs(diffs: List<Diff>): String {
        if (diffs.isEmpty()) return terminal.render(TextStyles.bold("No changes"))

        val sb = StringBuilder()
        var totalAdditions = 0
        var totalDeletions = 0
        var totalLines = 0

        for ((index, diff) in diffs.withIndex()) {
            if (index > 0) sb.appendLine()

            // File-level grouping header
            sb.appendLine(terminal.render(TextStyles.bold("─── ${diff.path} ───")))
            sb.appendLine()

            val fileLines = mutableListOf<String>()
            var fileAdditions = 0
            var fileDeletions = 0

            for (hunk in diff.hunks) {
                fileLines.add(terminal.render(TextColors.magenta("@@ -${hunk.oldStart} +${hunk.newStart} @@")))

                for (line in hunk.lines) {
                    when (line) {
                        is DiffLine.Added -> {
                            fileLines.add(terminal.render(TextColors.green("+${line.text}")))
                            fileAdditions++
                        }

                        is DiffLine.Removed -> {
                            fileLines.add(terminal.render(TextColors.red("-${line.text}")))
                            fileDeletions++
                        }

                        is DiffLine.Context -> {
                            fileLines.add(" ${line.text}")
                        }

                        is DiffLine.Modified -> {
                            if (line.wordDiffs.isNotEmpty()) {
                                val wordRendered = renderWordDiffs(line.wordDiffs)
                                fileLines.add(terminal.render(TextColors.red("-${line.oldText}")))
                                fileLines.add(terminal.render(TextColors.green("+$wordRendered")))
                            } else {
                                fileLines.add(terminal.render(TextColors.red("-${line.oldText}")))
                                fileLines.add(terminal.render(TextColors.green("+${line.newText}")))
                            }
                            fileAdditions++
                            fileDeletions++
                        }
                    }
                }
            }

            totalAdditions += fileAdditions
            totalDeletions += fileDeletions

            // Apply truncation per file
            if (fileLines.size > MAX_DIFF_LINES) {
                val truncated = fileLines.take(MAX_DIFF_LINES)
                truncated.forEach { sb.appendLine(it) }
                val more = fileLines.size - MAX_DIFF_LINES
                sb.appendLine()
                sb.appendLine(terminal.render(TextColors.yellow("… truncated ($more more lines) …")))
            } else {
                fileLines.forEach { sb.appendLine(it) }
            }

            totalLines += fileLines.size
        }

        // Statistics summary
        sb.appendLine()
        sb.appendLine(terminal.render(TextStyles.bold("─── Summary ───")))
        sb.appendLine("Files changed: ${diffs.size}")
        sb.appendLine("Additions: ${terminal.render(TextColors.green("+$totalAdditions"))}")
        sb.appendLine("Deletions: ${terminal.render(TextColors.red("-$totalDeletions"))}")
        sb.appendLine("Total lines: $totalLines")

        return sb.toString()
    }

    private fun renderWordDiffs(wordDiffs: List<WordDiff>): String {
        return buildString {
            for (wd in wordDiffs) {
                when (wd) {
                    is WordDiff.WordAdded -> append(terminal.render(TextColors.green(TextStyles.bold(wd.text))))
                    is WordDiff.WordRemoved -> append(terminal.render(TextColors.red(TextStyles.bold(wd.text))))
                    is WordDiff.WordContext -> append(wd.text)
                }
            }
        }
    }
}
