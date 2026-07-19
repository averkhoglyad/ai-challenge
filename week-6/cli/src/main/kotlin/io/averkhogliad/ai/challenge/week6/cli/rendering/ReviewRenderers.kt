package io.averkhogliad.ai.challenge.week6.cli.rendering

import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.week6.domain.review.Review
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewTrigger
import io.averkhogliad.ai.challenge.week6.domain.review.Severity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReviewRenderers(private val terminal: Terminal) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    fun renderHistoryTable(reviews: List<Review>): String {
        val table = table {
            header {
                row("ID", "Date", "Trigger", "Findings", "Summary")
            }
            body {
                reviews.forEach { review ->
                    val date = Instant.ofEpochMilli(review.createdAt).let { dateFormatter.format(it) }
                    val trigger = when (review.trigger) {
                        ReviewTrigger.AUTO -> "auto"
                        ReviewTrigger.MANUAL -> "manual"
                        ReviewTrigger.PR -> "PR"
                    }
                    val findings = "${review.findings.size} issue(s)"
                    val summary = review.summary?.take(60)?.replace("\n", " ") ?: "-"
                    row(
                        review.id.take(8),
                        date,
                        trigger,
                        findings,
                        summary,
                    )
                }
            }
        }
        return terminal.render(table)
    }

    fun renderDetail(review: Review): String = buildString {
        val date = Instant.ofEpochMilli(review.createdAt).let { dateFormatter.format(it) }
        appendLine("Review ${review.id.take(8)} — $date")
        appendLine("Trigger: ${review.trigger}")
        if (review.commitHash != null) appendLine("Commit: ${review.commitHash.take(8)}")
        if (review.branch != null) appendLine("Branch: ${review.branch}")
        if (review.prId != null) appendLine("PR: ${review.prId.take(8)}")
        appendLine()
        if (review.summary != null) {
            appendLine("Summary:")
            appendLine(review.summary)
            appendLine()
        }
        if (review.findings.isEmpty()) {
            appendLine("No findings.")
        } else {
            appendLine("Findings (${review.findings.size}):")
            appendLine()
            review.findings.forEachIndexed { i, f ->
                val sev = when (f.severity) {
                    Severity.CRITICAL -> "🔴 CRITICAL"
                    Severity.WARNING -> "🟡 WARNING"
                    Severity.INFO -> "🔵 INFO"
                }
                appendLine("${i + 1}. [$sev] [${f.category}] ${f.description}")
                if (f.file != null) {
                    val loc = buildString {
                        append(f.file)
                        if (f.line != null) append(":${f.line}")
                    }
                    appendLine("   📁 $loc")
                }
                if (f.recommendation != null) {
                    appendLine("   💡 ${f.recommendation}")
                }
                appendLine()
            }
        }
    }
}
