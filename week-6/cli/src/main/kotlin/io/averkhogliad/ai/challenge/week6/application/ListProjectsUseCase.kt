package io.averkhogliad.ai.challenge.week6.application

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.ai.challenge.week6.domain.port.ProjectRepository

class ListProjectsUseCase(
    private val projectRepository: ProjectRepository,
) {
    suspend fun execute(): DomainResult<List<Project>> =
        projectRepository.findAll()
}
