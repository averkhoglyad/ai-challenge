package io.averkhogliad.ai.challenge.week6.domain.indexer.usecase

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexedSource
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.SourceType
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedSourceRepository
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

class AddSourceUseCase(
    private val sourceRepository: IndexedSourceRepository,
) {
    suspend fun execute(projectId: String, path: Path): DomainResult<IndexedSource> {
        val absolutePath = path.toAbsolutePath().normalize()
        if (!Files.exists(absolutePath)) {
            return DomainResult.Failure(DomainError.pathNotFound(path.toString()))
        }
        val sourceType = when {
            Files.isRegularFile(absolutePath) -> SourceType.FILE
            Files.isDirectory(absolutePath) -> SourceType.DIRECTORY
            else -> return DomainResult.Failure(DomainError.notDirectory(path.toString()))
        }
        val source = IndexedSource(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            path = absolutePath,
            sourceType = sourceType,
            isDefault = false,
            createdAt = Instant.now(),
        )
        sourceRepository.addSource(source)
        return DomainResult.Success(source)
    }
}
