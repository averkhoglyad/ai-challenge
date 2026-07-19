package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.SearchHit
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.SearchQuery
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort

class FileSearchUseCase(
    private val fileOpsPort: FileOpsPort,
    private val projectContextProvider: ProjectContextProvider,
) {
    suspend fun execute(
        query: String,
        extension: String? = null,
        inDirectory: String? = null,
        caseSensitive: Boolean = false,
    ): DomainResult<List<SearchHit>> {
        val ctx = when (val r = projectContextProvider.getContext()) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> return DomainResult.Failure(r.error)
        } ?: return DomainResult.Failure(DomainError.NoActiveProject())

        val dir = if (inDirectory != null) {
            when (val r = RelativePath.from(inDirectory, ctx.rootPath)) {
                is DomainResult.Success -> r.value
                is DomainResult.Failure -> return DomainResult.Failure(r.error)
            }
        } else null

        val searchQuery = SearchQuery(
            query = query,
            ignoreCase = !caseSensitive,
            extension = extension,
            inDirectory = dir,
        )

        return fileOpsPort.search(searchQuery)
    }
}
