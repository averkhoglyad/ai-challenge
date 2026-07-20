package io.averkhogliad.ai.challenge.week6.unit.application.fileops

import io.averkhogliad.ai.challenge.week6.application.AgentLoopService
import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.fileops.DiffService
import io.averkhogliad.ai.challenge.week6.application.fileops.RefactorAgentOrchestrator
import io.averkhogliad.ai.challenge.week6.application.fileops.RefactorProgress
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileChange
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Path

class RefactorAgentOrchestratorTest : FreeSpec({

    "orchestrate" - {

        "emits structured error when JSON-shaped plan causes unexpected parsing exception" {
            runTest {
                // given
                val agentLoopService = mockk<AgentLoopService>()
                val fileOpsPort = mockk<FileOpsPort>()
                val projectContextProvider = mockk<ProjectContextProvider>()
                val rootPath = mockk<Path>()
                val context = ProjectContext("project-id", rootPath, emptyList(), isGitEnabled = false)
                val orchestrator = RefactorAgentOrchestrator(
                    agentLoopService,
                    fileOpsPort,
                    DiffService(),
                    projectContextProvider,
                )
                every { agentLoopService.processQuery(any(), any(), any(), any()) } returns
                        flowOf("[{\"path\": \"src/App.kt\", \"newContent\": \"updated\"}]")
                coEvery { projectContextProvider.getContext() } returns DomainResult.Success(context)
                every { rootPath.resolve(any<String>()) } throws IllegalStateException("path resolution failed")

                // when
                val progress = orchestrator.orchestrate("update application").toList()

                // then
                progress shouldBe listOf(
                    RefactorProgress.Planning,
                    RefactorProgress.Error("Не удалось разобрать план рефакторинга: path resolution failed"),
                )
            }
        }
    }

    "executeChanges" - {

        "surfaces rollback warning when write and restoration both fail" {
            runTest {
                // given
                val agentLoopService = mockk<AgentLoopService>()
                val fileOpsPort = mockk<FileOpsPort>()
                val projectContextProvider = mockk<ProjectContextProvider>()
                val orchestrator = RefactorAgentOrchestrator(
                    agentLoopService,
                    fileOpsPort,
                    DiffService(),
                    projectContextProvider,
                )
                val restoredPath = relativePath("src/Existing.kt")
                val failedPath = relativePath("src/Failed.kt")
                val changes = listOf(
                    FileChange(restoredPath, oldContent = "original", newContent = "updated"),
                    FileChange(failedPath, oldContent = "before", newContent = "new"),
                )
                coEvery { fileOpsPort.write(restoredPath, "updated") } returns DomainResult.Success(Unit)
                coEvery { fileOpsPort.write(failedPath, "new") } returns
                        DomainResult.Failure(DomainError.FileWriteDenied(failedPath, "disk full"))
                coEvery { fileOpsPort.write(restoredPath, "original") } returns
                        DomainResult.Failure(DomainError.FileWriteDenied(restoredPath, "permission denied"))

                // when
                val progress = orchestrator.executeChanges(changes, setOf(restoredPath, failedPath)).toList()

                // then
                progress.size shouldBe 2
                progress.first() shouldBe RefactorProgress.Executing
                val error = progress.last() as RefactorProgress.Error
                error.message shouldContain "Failed to write src/Failed.kt: Write denied for 'src/Failed.kt': disk full."
                error.message shouldContain "Rolled back 1 file(s)."
                error.message shouldContain "Предупреждения отката: Не удалось восстановить src/Existing.kt: Write denied for 'src/Existing.kt': permission denied."
            }
        }
    }
}) {
    companion object {
        private fun relativePath(path: String): RelativePath =
            RelativePath.from(path, Path.of(".").toAbsolutePath().normalize()).getOrThrow()
    }
}
