package io.averkhogliad.ai.challenge.week6.cli.rendering

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.week6.application.StartupIndexingProgress

class StartupIndexingRenderer(
    private val terminal: Terminal,
) {
    fun render(progress: StartupIndexingProgress) {
        when (progress) {
            is StartupIndexingProgress.ProjectLoaded ->
                terminal.println("Проект: ${progress.project.name}  (${progress.project.rootPath})")

            StartupIndexingProgress.IndexingStarted -> terminal.println("Индексация документации...")

            is StartupIndexingProgress.SourceComplete -> terminal.println(
                "  [${progress.index}/${progress.total}] ${progress.sourcePath} (${progress.chunkCount} чанков)"
            )

            is StartupIndexingProgress.Completed ->
                terminal.println("Индекс готов: ${progress.totalChunks} чанков, модель: ${progress.model}")

            is StartupIndexingProgress.Error ->
                terminal.println(TextColors.red("Ошибка индексации: ${progress.message}"))
        }
    }
}
