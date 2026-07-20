package io.averkhogliad.ai.challenge.week6.cli.handlers.indexer

import io.averkhogliad.ai.challenge.week6.application.StartupIndexingProgress
import io.averkhogliad.ai.challenge.week6.application.StartupIndexingUseCase
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import kotlinx.coroutines.flow.map

class IndexCommandHandler(
    private val startupIndexingUseCase: StartupIndexingUseCase,
    private val activeProjectProvider: () -> Project?,
) : CommandHandler {

    override val name: String = "/index"
    override val description: String = "Переиндексировать документацию активного проекта"

    override fun canHandle(rawInput: String): Boolean = rawInput == name

    override suspend fun execute(rawInput: String): CommandEffect {
        val project = activeProjectProvider()
            ?: return CommandEffect.Print("Нет активного проекта для индексации.", isError = true)

        return CommandEffect.StreamOutput(
            startupIndexingUseCase.execute(project).map(::formatProgress)
        )
    }

    private fun formatProgress(progress: StartupIndexingProgress): String = when (progress) {
        is StartupIndexingProgress.ProjectLoaded ->
            "Проект: ${progress.project.name}  (${progress.project.rootPath})"

        StartupIndexingProgress.IndexingStarted -> "Индексация документации..."

        is StartupIndexingProgress.SourceComplete ->
            "  [${progress.index}/${progress.total}] ${progress.sourcePath} (${progress.chunkCount} чанков)"

        is StartupIndexingProgress.Completed ->
            "Индекс готов: ${progress.totalChunks} чанков, модель: ${progress.model}"

        is StartupIndexingProgress.Error -> "Ошибка индексации: ${progress.message}"
    }
}
