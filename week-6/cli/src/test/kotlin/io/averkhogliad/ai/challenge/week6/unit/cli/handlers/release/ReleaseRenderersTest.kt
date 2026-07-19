package io.averkhogliad.ai.challenge.week6.unit.cli.handlers.release

import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.week6.cli.rendering.ReleaseDetailRenderer
import io.averkhogliad.ai.challenge.week6.cli.rendering.ReleaseTableRenderer
import io.averkhogliad.ai.challenge.week6.domain.release.model.Changelog
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitCategory
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitInfo
import io.averkhogliad.ai.challenge.week6.domain.release.model.Release
import io.averkhogliad.cli.repl.mordant.common.MarkdownRenderer
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import java.time.Instant
import java.time.LocalDate

class ReleaseRenderersTest : FreeSpec({
    "ReleaseDetailRenderer" - {
        "renders changelog sections" {
            val release = Release(
                "id", "project", "v1.0.0", null, "HEAD", emptyList(),
                Changelog("v1.0.0", LocalDate.of(2026, 1, 1), emptyList(), "summary"),
                Instant.EPOCH,
            )
            val rendered = ReleaseDetailRenderer(MarkdownRenderer(Terminal())).render(release)

            rendered shouldContain "v1.0.0"
            rendered shouldContain "summary"
        }
    }

    "ReleaseTableRenderer" - {
        "renders empty history message" {
            ReleaseTableRenderer(Terminal()).render(emptyList()) shouldContain "No releases found"
        }

        "renders release columns and data" {
            val release = Release(
                "id", "project", "v1.0.0", null, "HEAD",
                listOf(
                    CommitInfo(
                        "abcdef",
                        "abcdef",
                        "breaking: remove API",
                        "Ada",
                        Instant.EPOCH,
                        emptyList(),
                        CommitCategory.BREAKING,
                        null
                    ),
                ),
                Changelog("v1.0.0", LocalDate.of(2026, 1, 1), emptyList(), "summary"),
                Instant.EPOCH,
            )
            val rendered = ReleaseTableRenderer(Terminal()).render(listOf(release))

            rendered shouldContain "Version"
            rendered shouldContain "v1.0.0"
            rendered shouldContain "Breaking Changes"
        }
    }
})
