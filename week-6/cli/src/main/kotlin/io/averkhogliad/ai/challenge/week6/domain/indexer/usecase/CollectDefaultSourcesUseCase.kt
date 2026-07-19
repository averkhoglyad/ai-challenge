package io.averkhogliad.ai.challenge.week6.domain.indexer.usecase

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexedSource
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.SourceType
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

class CollectDefaultSourcesUseCase {

    fun execute(projectId: String, rootPath: Path): List<IndexedSource> {
        val sources = mutableListOf<IndexedSource>()

        val readme = rootPath.resolve("README.md")
        if (Files.exists(readme) && Files.isRegularFile(readme)) {
            sources.add(
                IndexedSource(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    path = readme,
                    sourceType = SourceType.FILE,
                    isDefault = true,
                    createdAt = Instant.now(),
                )
            )
        }

        val docsDir = rootPath.resolve("docs")
        if (Files.exists(docsDir) && Files.isDirectory(docsDir)) {
            sources.add(
                IndexedSource(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    path = docsDir,
                    sourceType = SourceType.DIRECTORY,
                    isDefault = true,
                    createdAt = Instant.now(),
                )
            )
        }

        return sources
    }
}
