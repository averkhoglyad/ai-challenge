package io.averkhogliad.ai.challenge.week4.cli.cli.indexer

import io.averkhogliad.ai.challenge.week4.cli.application.indexer.DocumentLoader
import io.averkhogliad.ai.challenge.week4.cli.application.indexer.IndexingPipeline
import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Обработчик команд индексации документов.
 *
 * Каждый метод принимает [Command] и [CliState], возвращает [CliState].
 * Вызовы suspend-функций репозитория оборачиваются в [runBlocking],
 * следуя паттерну существующих обработчиков CLI.
 */
class IndexCommandHandler(
    private val pipeline: IndexingPipeline,
    private val documentLoader: DocumentLoader,
    private val repository: IndexRepository,
    private val renderer: CliRenderer,
    private val fixedSizeChunker: ChunkingStrategy,
    private val structuralChunker: ChunkingStrategy
) {
    fun handleIndex(command: Command.Index, state: CliState): CliState {
        val strategy = when (command.strategy.lowercase()) {
            "fixed" -> fixedSizeChunker
            "structural" -> structuralChunker
            else -> {
                renderer.renderError("Unknown strategy: ${command.strategy}. Supported: fixed, structural")
                return state
            }
        }

        return runBlocking {
            try {
                renderer.renderInfo("Loading documents from: ${command.path}")
                val documents = documentLoader.load(command.path)
                renderer.renderInfo("Starting indexing with strategy: ${strategy.type}...")
                val runId = pipeline.execute(documents, command.path, strategy)
                renderer.renderInfo("Indexing completed. Run ID: $runId")
            } catch (e: Exception) {
                renderer.renderError("Indexing failed: ${e.message}")
            }
            state
        }
    }

    fun handleIndexRuns(state: CliState): CliState {
        return runBlocking {
            try {
                val runs = repository.getAllRuns()
                IndexResultRenderer.renderRuns(runs, renderer)
            } catch (e: Exception) {
                renderer.renderError("Failed to list runs: ${e.message}")
            }
            state
        }
    }

    fun handleIndexSwitch(command: Command.IndexSwitch, state: CliState): CliState {
        return runBlocking {
            try {
                val runId = UUID.fromString(command.runId)
                val run = repository.getRun(runId)
                if (run == null) {
                    renderer.renderError("Run not found: ${command.runId}")
                } else {
                    repository.setActiveIndex(runId)
                    renderer.renderInfo("Active index switched to: ${command.runId}")
                }
            } catch (e: IllegalArgumentException) {
                renderer.renderError("Invalid run ID format: ${command.runId}")
            } catch (e: Exception) {
                renderer.renderError("Failed to switch index: ${e.message}")
            }
            state
        }
    }

    fun handleIndexStats(command: Command.IndexStats, state: CliState): CliState {
        return runBlocking {
            try {
                if (command.runId == "all") {
                    val runs = repository.getAllRuns()
                    if (runs.isEmpty()) {
                        renderer.renderInfo("No indexing runs found.")
                    } else {
                        for (run in runs) {
                            try {
                                val stats = repository.getStatistics(run.id)
                                IndexResultRenderer.renderStats(stats, renderer)
                            } catch (_: NoSuchElementException) {
                                renderer.renderInfo("Run ${run.id}: no chunks yet (status: ${run.status})")
                            }
                        }
                    }
                } else if (command.runId != null) {
                    val runId = UUID.fromString(command.runId)
                    val stats = repository.getStatistics(runId)
                    IndexResultRenderer.renderStats(stats, renderer)
                } else {
                    // Показать статистику активного индекса
                    val activeId = repository.getActiveIndex()
                    if (activeId == null) {
                        renderer.renderInfo("No active index. Use :index-switch <runId> to select one.")
                    } else {
                        val stats = repository.getStatistics(activeId)
                        IndexResultRenderer.renderStats(stats, renderer)
                    }
                }
            } catch (e: IllegalArgumentException) {
                renderer.renderError("Invalid run ID format: ${command.runId}")
            } catch (e: NoSuchElementException) {
                renderer.renderError("No chunks found for run: ${command.runId}")
            } catch (e: Exception) {
                renderer.renderError("Failed to get statistics: ${e.message}")
            }
            state
        }
    }

    fun handleIndexCompare(command: Command.IndexCompare, state: CliState): CliState {
        return runBlocking {
            try {
                val id1 = UUID.fromString(command.runId1)
                val id2 = UUID.fromString(command.runId2)
                val stats1 = repository.getStatistics(id1)
                val stats2 = repository.getStatistics(id2)
                val comparison = io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexComparison(
                    run1 = stats1,
                    run2 = stats2
                )
                IndexResultRenderer.renderComparison(comparison, renderer)
            } catch (e: IllegalArgumentException) {
                renderer.renderError("Invalid run ID format. Expected UUIDs.")
            } catch (e: NoSuchElementException) {
                renderer.renderError("One or both runs not found or have no chunks.")
            } catch (e: Exception) {
                renderer.renderError("Failed to compare indexes: ${e.message}")
            }
            state
        }
    }

    fun handleIndexDelete(command: Command.IndexDelete, state: CliState): CliState {
        return runBlocking {
            try {
                val runId = UUID.fromString(command.runId)
                repository.deleteRun(runId)
                renderer.renderInfo("Run deleted: ${command.runId}")
            } catch (e: IllegalArgumentException) {
                renderer.renderError("Invalid run ID format: ${command.runId}")
            } catch (e: Exception) {
                renderer.renderError("Failed to delete run: ${e.message}")
            }
            state
        }
    }

    fun handleIndexDeleteBefore(command: Command.IndexDeleteBefore, state: CliState): CliState {
        return runBlocking {
            try {
                val instant = java.time.Instant.parse(command.date)
                repository.deleteRunsBefore(instant)
                renderer.renderInfo("Deleted all runs before: ${command.date}")
            } catch (e: java.time.format.DateTimeParseException) {
                renderer.renderError("Invalid date format: ${command.date}. Use ISO-8601 (e.g. 2024-01-01T00:00:00Z)")
            } catch (e: Exception) {
                renderer.renderError("Failed to delete runs: ${e.message}")
            }
            state
        }
    }

    fun handleIndexDeleteKeepLast(command: Command.IndexDeleteKeepLast, state: CliState): CliState {
        return runBlocking {
            try {
                repository.keepLastRuns(command.count)
                renderer.renderInfo("Kept last ${command.count} runs, deleted all older.")
            } catch (e: Exception) {
                renderer.renderError("Failed to clean old runs: ${e.message}")
            }
            state
        }
    }

    fun handleIndexClear(state: CliState): CliState {
        return runBlocking {
            try {
                val activeId = repository.getActiveIndex()
                repository.deleteAllRunsExcept(activeId)
                if (activeId != null) {
                    renderer.renderInfo("Cleared all runs except active index: $activeId")
                } else {
                    renderer.renderInfo("Cleared all runs (no active index).")
                }
            } catch (e: Exception) {
                renderer.renderError("Failed to clear runs: ${e.message}")
            }
            state
        }
    }

    fun handleIndexClearAll(state: CliState): CliState {
        return runBlocking {
            try {
                repository.deleteAllRunsExcept(null)
                renderer.renderInfo("Cleared all runs (including active index).")
            } catch (e: Exception) {
                renderer.renderError("Failed to clear all runs: ${e.message}")
            }
            state
        }
    }
}
