package io.averkhogliad.ai.challenge.week6.unit.domain.model

import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class ProjectContextTest : FreeSpec({

    "ProjectContext" - {

        "is constructed with all fields" {
            // given
            val projectId = "proj-1"
            val rootPath = Path.of("/tmp/test-project")
            val docsPaths = listOf(Path.of("/tmp/test-project/docs"), Path.of("/tmp/test-project/README.md"))

            // when
            val ctx = ProjectContext(
                projectId = projectId,
                rootPath = rootPath,
                docsPaths = docsPaths,
                isGitEnabled = true,
            )

            // then
            ctx.projectId shouldBe projectId
            ctx.rootPath shouldBe rootPath
            ctx.docsPaths shouldBe docsPaths
            ctx.isGitEnabled shouldBe true
        }

        "has empty docsPaths by default" {
            // given & when
            val ctx = ProjectContext(
                projectId = "proj-2",
                rootPath = Path.of("/tmp/test"),
                docsPaths = emptyList(),
                isGitEnabled = false,
            )

            // then
            ctx.docsPaths.isEmpty() shouldBe true
            ctx.isGitEnabled shouldBe false
        }

        "supports equality comparison" {
            // given
            val path = Path.of("/tmp/test")
            val ctx1 = ProjectContext("id", path, emptyList(), false)
            val ctx2 = ProjectContext("id", path, emptyList(), false)

            // then
            ctx1 shouldBe ctx2
        }
    }
})
