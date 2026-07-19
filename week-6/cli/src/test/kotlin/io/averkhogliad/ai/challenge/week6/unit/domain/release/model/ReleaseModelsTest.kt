package io.averkhogliad.ai.challenge.week6.unit.domain.release.model

import io.averkhogliad.ai.challenge.week6.domain.release.model.Changelog
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitCategory
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitInfo
import io.averkhogliad.ai.challenge.week6.domain.release.model.Release
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

class ReleaseModelsTest : FreeSpec({
    "Release models" - {
        "are immutable value objects with all release fields" {
            // given
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val commit =
                CommitInfo("abcdef", "abcdef", "feat: release", "Ada", now, emptyList(), CommitCategory.FEATURE, null)
            val changelog = Changelog("v1.0.0", LocalDate.of(2026, 1, 1), emptyList(), "summary")

            // when
            val release = Release("rel-1", "project-1", "v1.0.0", null, "HEAD~1..HEAD", listOf(commit), changelog, now)

            // then
            release.copy(version = "v1.0.1").version shouldBe "v1.0.1"
            release.commits.single().category shouldBe CommitCategory.FEATURE
            CommitCategory.entries.size shouldBe 8
        }
    }
})
