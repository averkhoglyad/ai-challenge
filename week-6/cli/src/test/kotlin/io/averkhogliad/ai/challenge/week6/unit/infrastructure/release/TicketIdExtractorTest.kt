package io.averkhogliad.ai.challenge.week6.unit.infrastructure.release

import io.averkhogliad.ai.challenge.week6.infrastructure.release.TicketIdExtractor
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly

class TicketIdExtractorTest : FreeSpec({
    val extractor = TicketIdExtractor()

    "extract" - {
        "returns issue and project ticket identifiers in encounter order" {
            // given
            val message = "fix: resolve PROJ-456 (closes #42), related to JIRA-789"

            // when
            val tickets = extractor.extract(message)

            // then
            tickets shouldContainExactly listOf("#42", "PROJ-456", "JIRA-789")
        }

        "removes duplicate ticket identifiers" {
            // given
            val message = "refs #12 and PROJ-1; closes #12 and PROJ-1"

            // when
            val tickets = extractor.extract(message)

            // then
            tickets shouldContainExactly listOf("#12", "PROJ-1")
        }

        "returns empty list when message has no ticket identifiers" {
            // when
            val tickets = extractor.extract("chore: update tooling")

            // then
            tickets shouldContainExactly emptyList()
        }
    }
})
