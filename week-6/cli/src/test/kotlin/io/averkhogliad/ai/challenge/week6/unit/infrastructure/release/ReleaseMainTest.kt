package io.averkhogliad.ai.challenge.week6.unit.infrastructure.release

import io.averkhogliad.ai.challenge.week6.infrastructure.release.ReleaseMain
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class ReleaseMainTest : FreeSpec({
    "ReleaseMain.parse" - {
        "parses release arguments" {
            val result = ReleaseMain.parse(
                arrayOf("--release", "--project-id", "project-1", "--version", "v1.2.3", "--range", "v1.2.2..HEAD"),
            ).getOrThrow()

            result.projectId shouldBe "project-1"
            result.version shouldBe "v1.2.3"
            result.range shouldBe "v1.2.2..HEAD"
        }

        "rejects missing project id" {
            ReleaseMain.parse(arrayOf("--release", "--version", "v1.2.3")).isFailure shouldBe true
        }

        "rejects unknown options" {
            ReleaseMain.parse(
                arrayOf(
                    "--release",
                    "--project-id",
                    "project-1",
                    "--unknown",
                    "value"
                )
            ).isFailure shouldBe true
        }
    }
})
