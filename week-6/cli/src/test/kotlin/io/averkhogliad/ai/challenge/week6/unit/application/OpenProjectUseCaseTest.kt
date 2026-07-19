package io.averkhogliad.ai.challenge.week6.unit.application

import io.averkhogliad.ai.challenge.week6.application.OpenProjectUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.port.AppStateRepository
import io.averkhogliad.ai.challenge.week6.domain.port.ProjectRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path

class OpenProjectUseCaseTest : FreeSpec({

    lateinit var projectRepository: ProjectRepository
    lateinit var appStateRepository: AppStateRepository
    lateinit var useCase: OpenProjectUseCase

    beforeEach {
        projectRepository = mockk()
        appStateRepository = mockk()
        useCase = OpenProjectUseCase(projectRepository, appStateRepository)
    }

    "execute" - {

        "returns PathNotFound error when path does not exist" {
            runTest {
                // given
                val nonExistentPath = "/tmp/non_existent_path_12345_test"
                val expectedNormalized = Path.of(nonExistentPath).toAbsolutePath().normalize().toString()

                // when
                val result = useCase.execute(nonExistentPath)

                // then
                result.isFailure shouldBe true
                val failure = result as DomainResult.Failure
                failure.error.shouldBeInstanceOf<DomainError.PathNotFound>()
                failure.error.message shouldBe "Путь не найден: $expectedNormalized"
            }
        }

        "returns PathNotDirectory error when path is a file" {
            runTest {
                // given
                val tempFile = Files.createTempFile("week6-test-", ".txt")
                val pathStr = tempFile.toString()
                val expectedNormalized = tempFile.toAbsolutePath().normalize().toString()

                // when
                val result = useCase.execute(pathStr)

                // then
                result.isFailure shouldBe true
                val failure = result as DomainResult.Failure
                failure.error.shouldBeInstanceOf<DomainError.PathNotDirectory>()
                failure.error.message shouldBe "Указанный путь не является директорией: $expectedNormalized"

                // cleanup
                Files.deleteIfExists(tempFile)
            }
        }

        "returns project when path exists and is a directory" {
            runTest {
                // given
                val tempDir = Files.createTempDirectory("week6-test-project-")
                val pathStr = tempDir.toString()
                val pathObj = tempDir.toAbsolutePath().normalize()

                coEvery { projectRepository.findByPath(pathObj.toString()) } returns
                        DomainResult.Success(null)

                val savedProjectSlot = slot<Project>()
                coEvery { projectRepository.save(capture(savedProjectSlot)) } answers {
                    DomainResult.Success(savedProjectSlot.captured)
                }
                coEvery { appStateRepository.setValue("active_project_id", any()) } returns
                        DomainResult.Success(Unit)

                // when
                val result = useCase.execute(pathStr)

                // then
                val project = (result as DomainResult.Success).value
                project.name shouldBe tempDir.fileName.toString()
                project.rootPath shouldBe pathObj

                coVerify { projectRepository.findByPath(pathObj.toString()) }
                coVerify { projectRepository.save(any<Project>()) }
                coVerify { appStateRepository.setValue("active_project_id", project.id) }

                // cleanup
                Files.deleteIfExists(tempDir)
            }
        }

        "saves project and sets active project id" {
            runTest {
                // given
                val tempDir = Files.createTempDirectory("week6-test-save-")
                val pathStr = tempDir.toString()
                val pathObj = tempDir.toAbsolutePath().normalize()

                coEvery { projectRepository.findByPath(pathObj.toString()) } returns
                        DomainResult.Success(null)

                val savedProjectSlot = slot<Project>()
                coEvery { projectRepository.save(capture(savedProjectSlot)) } answers {
                    DomainResult.Success(savedProjectSlot.captured)
                }
                coEvery { appStateRepository.setValue("active_project_id", any()) } returns
                        DomainResult.Success(Unit)

                // when
                useCase.execute(pathStr)

                // then
                val savedProject = savedProjectSlot.captured
                savedProject.rootPath shouldBe pathObj

                coVerify { appStateRepository.setValue("active_project_id", savedProject.id) }

                // cleanup
                Files.deleteIfExists(tempDir)
            }
        }
    }
})
