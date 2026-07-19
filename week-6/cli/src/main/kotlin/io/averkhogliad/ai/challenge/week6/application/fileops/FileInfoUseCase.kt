package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileMetadata
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort

class FileInfoUseCase(
    private val fileOpsPort: FileOpsPort,
    private val projectContextProvider: ProjectContextProvider,
) {
    suspend fun execute(pathStr: String): DomainResult<FileMetadata> {
        val ctx = when (val r = projectContextProvider.getContext()) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> return DomainResult.Failure(r.error)
        } ?: return DomainResult.Failure(DomainError.NoActiveProject())

        val relPath = when (val r = RelativePath.from(pathStr, ctx.rootPath)) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> return DomainResult.Failure(r.error)
        }

        return fileOpsPort.info(relPath)
    }
}
