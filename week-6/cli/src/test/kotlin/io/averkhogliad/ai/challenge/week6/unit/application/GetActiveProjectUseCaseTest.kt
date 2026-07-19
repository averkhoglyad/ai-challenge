package io.averkhogliad.ai.challenge.week6.unit.application

import io.averkhogliad.ai.challenge.week6.application.GetActiveProjectUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.port.AppStateRepository
import io.averkhogliad.ai.challenge.week6.domain.port.ProjectRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import java.time.Instant

class GetActiveProjectUseCaseTest : FreeSpec({

    lateinit var appStateRepository: AppStateRepository
    lateinit var projectRepository: ProjectRepository
    lateinit var useCase: GetActiveProjectUseCase

    beforeEach {
        appStateRepository = mockk()
        projectRepository = mockk()
        useCase = GetActiveProjectUseCase(appStateRepository, projectRepository)
    }

    "execute" - {

        "returns null when no active project" {
            runTest {
                // given
                coEvery { appStateRepository.getValue("active_project_id") } returns
                        DomainResult.Success(null)

                // when
                val result = useCase.execute()

                // then
                (result as DomainResult.Success).value.shouldBeNull()
            }
        }

        "returns project when active project set" {
            runTest {
                // given
                val now = Instant.now()
                val project = Project(
                    id = "proj-1",
                    name = "my-project",
                    rootPath = Path.of("/tmp/my-project"),
                    createdAt = now,
                    updatedAt = now,
                )
                coEvery { appStateRepository.getValue("active_project_id") } returns
                        DomainResult.Success("proj-1")
                coEvery { projectRepository.findById("proj-1") } returns
                        DomainResult.Success(project)

                // when
                val result = useCase.execute()

                // then
                (result as DomainResult.Success).value shouldBe project
            }
        }
    }
})
