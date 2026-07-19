package io.averkhogliad.ai.challenge.week6.application.pr

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.pr.PrStatus
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequest
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequestRepository
import java.nio.file.Path
import java.util.UUID

class CreatePullRequestUseCase(
    private val prRepository: PullRequestRepository,
    private val gitPort: GitPort,
) {
    data class CreatePrRequest(
        val projectId: String,
        val rootPath: Path,
        val title: String,
        val sourceBranch: String? = null,
        val targetBranch: String? = null,
    )

    suspend fun execute(request: CreatePrRequest): DomainResult<PullRequest> {
        val source = request.sourceBranch
            ?: gitPort.getCurrentBranch(request.rootPath).getOrNull()
            ?: return DomainResult.Failure(DomainError.repository("Cannot determine source branch"))

        val target = request.targetBranch
            ?: return DomainResult.Failure(DomainError.repository("Target branch is required for first PR"))

        if (source == target) {
            return DomainResult.Failure(DomainError.repository("Source and target branches must be different"))
        }

        val sourceExists = gitPort.branchExists(request.rootPath, source)
        if (sourceExists is DomainResult.Success && !sourceExists.value) {
            return DomainResult.Failure(DomainError.repository("Source branch '$source' does not exist"))
        }

        val targetExists = gitPort.branchExists(request.rootPath, target)
        if (targetExists is DomainResult.Success && !targetExists.value) {
            return DomainResult.Failure(DomainError.repository("Target branch '$target' does not exist"))
        }

        val pr = PullRequest(
            id = UUID.randomUUID().toString(),
            projectId = request.projectId,
            title = request.title,
            sourceBranch = source,
            targetBranch = target,
        )

        prRepository.save(pr)
        return DomainResult.Success(pr)
    }
}

class ListPullRequestsUseCase(
    private val prRepository: PullRequestRepository,
) {
    suspend fun execute(projectId: String, statusFilter: PrStatus? = null): List<PullRequest> {
        return prRepository.findByProjectId(projectId, statusFilter)
    }
}

class GetPullRequestDiffUseCase(
    private val gitPort: GitPort,
) {
    suspend fun execute(rootPath: Path, pr: PullRequest): DomainResult<String> {
        return gitPort.getDiffBetweenBranches(rootPath, pr.sourceBranch, pr.targetBranch)
    }
}
