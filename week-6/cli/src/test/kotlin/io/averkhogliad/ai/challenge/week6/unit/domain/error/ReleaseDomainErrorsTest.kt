package io.averkhogliad.ai.challenge.week6.unit.domain.error

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class ReleaseDomainErrorsTest : FreeSpec({
    "Release domain errors" - {
        "inherit DomainError" {
            listOf(
                DomainError.releaseNotFound("rel-1"),
                DomainError.invalidVersionFormat("one"),
                DomainError.gitRangeNotFound("bad.."),
                DomainError.noCommitsInRange("HEAD~1..HEAD"),
            ).forEach { error -> error.shouldBeInstanceOf<DomainError>() }
        }
    }
})
