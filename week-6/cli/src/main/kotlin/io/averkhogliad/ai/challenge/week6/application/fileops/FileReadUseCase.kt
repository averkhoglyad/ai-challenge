package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileContent
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileFilter
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileMetadata
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort

class FileReadUseCase(
    private val fileOpsPort: FileOpsPort,
    private val projectContextProvider: ProjectContextProvider,
) {
    suspend fun execute(pathStr: String): DomainResult<FileContent> {
        val ctx = when (val r = projectContextProvider.getContext()) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> return DomainResult.Failure(r.error)
        } ?: return DomainResult.Failure(DomainError.NoActiveProject())

        val relPath = when (val r = RelativePath.from(pathStr, ctx.rootPath)) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> return DomainResult.Failure(r.error)
        }

        return fileOpsPort.read(relPath)
    }
}

class FileListUseCase(
    private val fileOpsPort: FileOpsPort,
    private val projectContextProvider: ProjectContextProvider,
) {
    suspend fun execute(dirStr: String = ".", extension: String? = null): DomainResult<List<FileMetadata>> {
        val ctx = when (val r = projectContextProvider.getContext()) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> return DomainResult.Failure(r.error)
        } ?: return DomainResult.Failure(DomainError.NoActiveProject())

        val relDir = when (val r = RelativePath.from(dirStr, ctx.rootPath)) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> return DomainResult.Failure(r.error)
        }

        return fileOpsPort.list(relDir, FileFilter(extension = extension))
    }
}
