package io.averkhogliad.ai.challenge.week6.unit.application.pr

import io.averkhogliad.ai.challenge.week6.application.pr.CreatePullRequestUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.GetPullRequestDiffUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.ListPullRequestsUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.pr.PrStatus
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequest
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequestRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Path

class PrUseCasesTest : FreeSpec({

    lateinit var prRepository: PullRequestRepository
    lateinit var gitPort: GitPort
    val rootPath = Path.of("/tmp/test-project")

    beforeEach {
        prRepository = mockk(relaxed = true)
        gitPort = mockk()
    }

    "CreatePullRequestUseCase" - {

        val testPath = Path.of("/tmp/test-project")

        "creates PR with valid branches" {
            runTest {
                // given
                val useCase = CreatePullRequestUseCase(prRepository, gitPort)
                coEvery { gitPort.getCurrentBranch(testPath) } returns DomainResult.Success("main")
                coEvery { gitPort.branchExists(testPath, "feature") } returns DomainResult.Success(true)
                coEvery { gitPort.branchExists(testPath, "main") } returns DomainResult.Success(true)
                coEvery { prRepository.save(any<PullRequest>()) } returns Unit

                // when
                val result = useCase.execute(
                    CreatePullRequestUseCase.CreatePrRequest(
                        projectId = "proj-1",
                        rootPath = testPath,
                        title = "Add feature X",
                        sourceBranch = "feature",
                        targetBranch = "main",
                    )
                )

                // then
                result.isSuccess shouldBe true
                val pr = (result as DomainResult.Success).value
                pr.title shouldBe "Add feature X"
                pr.sourceBranch shouldBe "feature"
                pr.targetBranch shouldBe "main"
                pr.status shouldBe PrStatus.OPEN
                coVerify(exactly = 1) { prRepository.save(any<PullRequest>()) }
            }
        }

        "fails when source and target branches are the same" {
            runTest {
                // given
                val useCase = CreatePullRequestUseCase(prRepository, gitPort)

                // when
                val result = useCase.execute(
                    CreatePullRequestUseCase.CreatePrRequest(
                        projectId = "proj-1",
                        rootPath = testPath,
                        title = "Test",
                        sourceBranch = "main",
                        targetBranch = "main",
                    )
                )

                // then
                result.isFailure shouldBe true
                val failure = result as DomainResult.Failure
                failure.error.message shouldContain "Source and target branches must be different"
            }
        }

        "fails when source branch does not exist" {
            runTest {
                // given
                val useCase = CreatePullRequestUseCase(prRepository, gitPort)
                coEvery { gitPort.branchExists(testPath, "nonexistent") } returns DomainResult.Success(false)

                // when
                val result = useCase.execute(
                    CreatePullRequestUseCase.CreatePrRequest(
                        projectId = "proj-1",
                        rootPath = testPath,
                        title = "Test",
                        sourceBranch = "nonexistent",
                        targetBranch = "main",
                    )
                )

                // then
                result.isFailure shouldBe true
                val failure = result as DomainResult.Failure
                failure.error.message shouldContain "Source branch 'nonexistent' does not exist"
            }
        }

        "fails when target branch does not exist" {
            runTest {
                // given
                val useCase = CreatePullRequestUseCase(prRepository, gitPort)
                coEvery { gitPort.branchExists(testPath, "feature") } returns DomainResult.Success(true)
                coEvery { gitPort.branchExists(testPath, "nonexistent") } returns DomainResult.Success(false)

                // when
                val result = useCase.execute(
                    CreatePullRequestUseCase.CreatePrRequest(
                        projectId = "proj-1",
                        rootPath = testPath,
                        title = "Test",
                        sourceBranch = "feature",
                        targetBranch = "nonexistent",
                    )
                )

                // then
                result.isFailure shouldBe true
                val failure = result as DomainResult.Failure
                failure.error.message shouldContain "Target branch 'nonexistent' does not exist"
            }
        }

        "fails when no target branch specified" {
            runTest {
                // given
                val useCase = CreatePullRequestUseCase(prRepository, gitPort)
                coEvery { gitPort.getCurrentBranch(testPath) } returns DomainResult.Success("feature")

                // when
                val result = useCase.execute(
                    CreatePullRequestUseCase.CreatePrRequest(
                        projectId = "proj-1",
                        rootPath = testPath,
                        title = "Test",
                        sourceBranch = null,
                        targetBranch = null,
                    )
                )

                // then
                result.isFailure shouldBe true
                val failure = result as DomainResult.Failure
                failure.error.message shouldContain "Target branch is required for first PR"
            }
        }
    }

    "ListPullRequestsUseCase" - {

        "returns PRs from repository" {
            runTest {
                // given
                val useCase = ListPullRequestsUseCase(prRepository)
                val prs = listOf(
                    PullRequest("1", "proj-1", "PR 1", "f1", "main", PrStatus.OPEN),
                    PullRequest("2", "proj-1", "PR 2", "f2", "main", PrStatus.OPEN),
                )
                coEvery { prRepository.findByProjectId("proj-1", null) } returns prs

                // when
                val result = useCase.execute("proj-1")

                // then
                result shouldHaveSize 2
                result[0].title shouldBe "PR 1"
            }
        }

        "filters by status" {
            runTest {
                // given
                val useCase = ListPullRequestsUseCase(prRepository)
                val openPrs = listOf(
                    PullRequest("1", "proj-1", "Open PR", "f1", "main", PrStatus.OPEN),
                )
                coEvery { prRepository.findByProjectId("proj-1", PrStatus.OPEN) } returns openPrs

                // when
                val result = useCase.execute("proj-1", PrStatus.OPEN)

                // then
                result shouldHaveSize 1
                result[0].status shouldBe PrStatus.OPEN
            }
        }

        "returns empty list for project with no PRs" {
            runTest {
                // given
                val useCase = ListPullRequestsUseCase(prRepository)
                coEvery { prRepository.findByProjectId("proj-empty", null) } returns emptyList()

                // when
                val result = useCase.execute("proj-empty")

                // then
                result.shouldBeEmpty()
            }
        }
    }

    "GetPullRequestDiffUseCase" - {

        "returns diff between branches" {
            runTest {
                // given
                val testPath = Path.of("/tmp/diff-project")
                val useCase = GetPullRequestDiffUseCase(gitPort)
                val pr = PullRequest("1", "proj-1", "PR", "feature", "main", PrStatus.OPEN)
                coEvery { gitPort.getDiffBetweenBranches(testPath, "feature", "main") } returns
                        DomainResult.Success("+added line\n-removed line")

                // when
                val result = useCase.execute(testPath, pr)

                // then
                result.isSuccess shouldBe true
                (result as DomainResult.Success).value shouldBe "+added line\n-removed line"
                coVerify { gitPort.getDiffBetweenBranches(testPath, "feature", "main") }
            }
        }

        "returns failure when git fails" {
            runTest {
                // given
                val testPath = Path.of("/tmp/diff-project")
                val useCase = GetPullRequestDiffUseCase(gitPort)
                val pr = PullRequest("1", "proj-1", "PR", "feature", "main", PrStatus.OPEN)
                coEvery { gitPort.getDiffBetweenBranches(testPath, "feature", "main") } returns
                        DomainResult.Failure(DomainError.repository("Git error"))

                // when
                val result = useCase.execute(testPath, pr)

                // then
                result.isFailure shouldBe true
            }
        }
    }
})
