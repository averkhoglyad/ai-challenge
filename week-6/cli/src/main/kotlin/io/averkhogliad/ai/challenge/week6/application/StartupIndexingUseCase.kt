package io.averkhogliad.ai.challenge.week6.application

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexProgress
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedSourceRepository
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.CollectDefaultSourcesUseCase
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.IndexSourcesUseCase
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StartupIndexingUseCase(
    private val sourceRepository: IndexedSourceRepository,
    private val collectDefaultSourcesUseCase: CollectDefaultSourcesUseCase,
    private val indexSourcesUseCase: IndexSourcesUseCase,
) {
    fun execute(project: Project): Flow<StartupIndexingProgress> = flow {
        emit(StartupIndexingProgress.ProjectLoaded(project))

        try {
            var sources = sourceRepository.findByProjectId(project.id)
            if (sources.isEmpty()) {
                sources = collectDefaultSourcesUseCase.execute(project.id, project.rootPath)
                sources.forEach { sourceRepository.addSource(it) }
            }

            emit(StartupIndexingProgress.IndexingStarted)
            indexSourcesUseCase.execute(sources, project.id, project.rootPath).collect { progress ->
                when (progress) {
                    is IndexProgress.SourceComplete -> emit(
                        StartupIndexingProgress.SourceComplete(
                            progress.index,
                            progress.total,
                            progress.sourcePath,
                            progress.chunkCount,
                        )
                    )

                    is IndexProgress.Completed -> emit(
                        StartupIndexingProgress.Completed(progress.totalChunks, progress.model)
                    )

                    is IndexProgress.Error -> emit(
                        StartupIndexingProgress.Error("${progress.source}: ${progress.cause}")
                    )

                    else -> Unit
                }
            }
        } catch (e: Exception) {
            emit(StartupIndexingProgress.Error(e.message ?: "неизвестная ошибка"))
        }
    }
}

sealed interface StartupIndexingProgress {
    data class ProjectLoaded(val project: Project) : StartupIndexingProgress
    data object IndexingStarted : StartupIndexingProgress
    data class SourceComplete(
        val index: Int,
        val total: Int,
        val sourcePath: String,
        val chunkCount: Int,
    ) : StartupIndexingProgress

    data class Completed(val totalChunks: Int, val model: String) : StartupIndexingProgress
    data class Error(val message: String) : StartupIndexingProgress
}
