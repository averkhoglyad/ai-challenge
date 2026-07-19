package io.averkhogliad.ai.challenge.week6.unit.infrastructure.release

import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitCategory
import io.averkhogliad.ai.challenge.week6.infrastructure.release.ConventionalCommitParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class ConventionalCommitParserTest : FreeSpec({
    val parser = ConventionalCommitParser()

    "parse" - {
        withData(
            nameFn = { "returns ${it.expected} for ${it.message}" },
            ParserCase("feat: add release automation", CommitCategory.FEATURE),
            ParserCase("fix(parser): handle empty commit", CommitCategory.FIX),
            ParserCase("docs: describe release flow", CommitCategory.DOCS),
            ParserCase("refactor!: replace legacy release code", CommitCategory.REFACTOR),
            ParserCase("perf: speed up history loading", CommitCategory.PERFORMANCE),
            ParserCase("chore: update dependencies", CommitCategory.CHORE),
            ParserCase("breaking: remove old endpoint", CommitCategory.BREAKING),
            ParserCase("ordinary commit message", CommitCategory.UNKNOWN),
            ParserCase("Merge branch 'main'", CommitCategory.UNKNOWN),
        ) { case ->
            // when
            val result = parser.parse(case.message)

            // then
            result shouldBe case.expected
        }

        "returns BREAKING when footer declares breaking change" {
            // given
            val message = "feat: change client\n\nBREAKING CHANGE: old API was removed"

            // when
            val result = parser.parse(message)

            // then
            result shouldBe CommitCategory.BREAKING
        }

        "never throws for arbitrary messages" {
            // given
            val messages = listOf("", "\n", "🔥", "fix", "feat(scope):")

            // when / then
            messages.forEach { parser.parse(it) shouldBe parser.parse(it) }
        }
    }
}) {
    private data class ParserCase(val message: String, val expected: CommitCategory)
}
