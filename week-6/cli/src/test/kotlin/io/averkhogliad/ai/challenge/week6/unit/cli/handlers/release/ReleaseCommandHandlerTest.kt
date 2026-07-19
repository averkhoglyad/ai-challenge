package io.averkhogliad.ai.challenge.week6.unit.cli.handlers.release

import io.averkhogliad.ai.challenge.week6.application.release.ConfirmReleaseUseCase
import io.averkhogliad.ai.challenge.week6.application.release.GenerateReleaseNotesUseCase
import io.averkhogliad.ai.challenge.week6.application.release.ReleaseDraft
import io.averkhogliad.ai.challenge.week6.application.release.ReleaseProgress
import io.averkhogliad.ai.challenge.week6.cli.handlers.release.ReleaseCommandHandler
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.release.model.Changelog
import io.averkhogliad.ai.challenge.week6.domain.release.model.Release
import io.averkhogliad.cli.repl.core.CommandEffect
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

class ReleaseCommandHandlerTest : FreeSpec({

    "rejects confirmation when active project changed after preview" {
        runTest {
            // given
            val initialProject = project("first", Path.of("first"))
            val currentProject = project("second", Path.of("second"))
            val draft = ReleaseDraft(
                Release(
                    id = "release",
                    projectId = initialProject.id,
                    version = "v1.0.0",
                    previousVersion = null,
                    range = "HEAD",
                    commits = emptyList(),
                    changelog = Changelog("v1.0.0", LocalDate.of(2026, 1, 1), emptyList(), ""),
                    createdAt = Instant.EPOCH,
                ),
                markdown = "# v1.0.0",
            )
            val generateReleaseNotesUseCase = mockk<GenerateReleaseNotesUseCase>()
            every { generateReleaseNotesUseCase.execute(any()) } returns flowOf(ReleaseProgress.PreviewReady(draft))
            var factoryProject: Project? = null
            val handler = ReleaseCommandHandler(
                generateReleaseNotesUseCase,
                confirmReleaseUseCaseFactory = { project ->
                    factoryProject = project
                    mockk<ConfirmReleaseUseCase>()
                },
                activeProject = object : () -> Project? {
                    private var calls = 0
                    override fun invoke(): Project = if (calls++ == 0) initialProject else currentProject
                },
            )

            // when
            val preview = handler.execute("/release") as CommandEffect.Confirm
            val result = preview.onConfirm()

            // then
            (result as CommandEffect.Print).isError shouldBe true
            result.message shouldBe "Active project changed. Generate release preview again."
            factoryProject shouldBe null
        }
    }
})

private fun project(id: String, rootPath: Path): Project = Project(
    id = id,
    name = id,
    rootPath = rootPath,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
