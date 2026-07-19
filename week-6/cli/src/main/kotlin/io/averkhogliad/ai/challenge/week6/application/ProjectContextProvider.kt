package io.averkhogliad.ai.challenge.week6.application

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import java.nio.file.Files
import java.nio.file.Path

class ProjectContextProvider(
    private val getActiveProjectUseCase: GetActiveProjectUseCase,
) {
    suspend fun getContext(): DomainResult<ProjectContext?> {
        val activeProject = when (val result = getActiveProjectUseCase.execute()) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return DomainResult.Failure(result.error)
        } ?: return DomainResult.Success(null)

        val rootPath = activeProject.rootPath
        val docsPaths = mutableListOf<Path>()

        val docsDir = rootPath.resolve("docs")
        if (Files.exists(docsDir) && Files.isDirectory(docsDir)) {
            docsPaths.add(docsDir)
        }

        val readmeFile = rootPath.resolve("README.md")
        if (Files.exists(readmeFile) && Files.isRegularFile(readmeFile)) {
            docsPaths.add(readmeFile)
        }

        val isGitEnabled = Files.exists(rootPath.resolve(".git"))

        return DomainResult.Success(
            ProjectContext(
                projectId = activeProject.id,
                rootPath = rootPath,
                docsPaths = docsPaths,
                isGitEnabled = isGitEnabled,
            )
        )
    }
}
