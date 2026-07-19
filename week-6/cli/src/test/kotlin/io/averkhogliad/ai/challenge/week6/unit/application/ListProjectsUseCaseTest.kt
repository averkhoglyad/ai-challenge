package io.averkhogliad.ai.challenge.week6.unit.application

import io.averkhogliad.ai.challenge.week6.application.ListProjectsUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.port.ProjectRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import java.time.Instant

class ListProjectsUseCaseTest : FreeSpec({

    lateinit var projectRepository: ProjectRepository
    lateinit var useCase: ListProjectsUseCase

    beforeEach {
        projectRepository = mockk()
        useCase = ListProjectsUseCase(projectRepository)
    }

    "execute" - {

        "returns empty list when no projects" {
            runTest {
                // given
                coEvery { projectRepository.findAll() } returns
                        DomainResult.Success(emptyList())

                // when
                val result = useCase.execute()

                // then
                (result as DomainResult.Success).value shouldBe emptyList()
            }
        }

        "returns projects from repository" {
            runTest {
                // given
                val now = Instant.now()
                val projects = listOf(
                    Project(
                        id = "1",
                        name = "project-a",
                        rootPath = Path.of("/tmp/project-a"),
                        createdAt = now,
                        updatedAt = now,
                    ),
                    Project(
                        id = "2",
                        name = "project-b",
                        rootPath = Path.of("/tmp/project-b"),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                coEvery { projectRepository.findAll() } returns
                        DomainResult.Success(projects)

                // when
                val result = useCase.execute()

                // then
                val value = (result as DomainResult.Success).value
                value.size shouldBe 2
                value[0].name shouldBe "project-a"
                value[1].name shouldBe "project-b"
            }
        }
    }
})
