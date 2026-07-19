package io.averkhogliad.ai.challenge.week6.application

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.port.AppStateRepository
import io.averkhogliad.ai.challenge.week6.domain.port.ProjectRepository

class GetActiveProjectUseCase(
    private val appStateRepository: AppStateRepository,
    private val projectRepository: ProjectRepository,
) {
    companion object {
        private const val ACTIVE_PROJECT_KEY = "active_project_id"
    }

    suspend fun execute(): DomainResult<Project?> {
        val activeId = when (val result = appStateRepository.getValue(ACTIVE_PROJECT_KEY)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return DomainResult.Failure(result.error)
        }

        if (activeId == null) return DomainResult.Success(null)

        return projectRepository.findById(activeId)
    }
}
