package io.averkhogliad.ai.challenge.week4.cli.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.MetricsAnalyzer
import io.averkhogliad.ai.challenge.week4.cli.application.rag.QueryHistoryService
import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagConfigService
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.RunStatus
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import kotlinx.coroutines.runBlocking

/**
 * Обработчик RAG-команд.
 *
 * Каждый метод принимает команду и текущее состояние, возвращает обновлённое состояние.
 * Вызовы suspend-функций оборачиваются в [runBlocking].
 */
class RagCommandHandler(
    private val indexRepository: IndexRepository,
    private val ragRenderer: RagCommandRenderer,
    private val configService: RagConfigService = RagConfigService(),
    private val historyService: QueryHistoryService? = null,
    private val metricsAnalyzer: MetricsAnalyzer? = null,
    private val historyRenderer: QueryHistoryRenderer = QueryHistoryRenderer(),
    private val analysisRenderer: MetricsAnalysisRenderer = MetricsAnalysisRenderer()
) {

    /**
     * Диспетчеризует [RagCommand] и возвращает обновлённое состояние.
     */
    fun handle(command: RagCommand, state: CliState): CliState = when (command) {
        is RagCommand.Toggle -> handleToggle(state)
        is RagCommand.Status -> handleStatus(state)
        is RagCommand.List -> handleList(state)
        // Новые команды Task 3
        is RagCommand.SetMode -> handleSetMode(command, state)
        is RagCommand.SetThreshold -> handleSetThreshold(command, state)
        is RagCommand.SetTopK -> handleSetTopK(command, state)
        is RagCommand.Config -> handleConfig(state)
        is RagCommand.History -> handleHistory(command, state)
        is RagCommand.HistoryDetail -> handleHistoryDetail(command, state)
        is RagCommand.Analyze -> handleAnalyze(state)
        is RagCommand.Compare -> handleCompare(command, state)
        is RagCommand.HistoryClear -> handleHistoryClear(state)
    }

    private fun handleToggle(state: CliState): CliState {
        val newEnabled = !state.ragState.enabled
        val newRagState = state.ragState.copy(enabled = newEnabled)

        if (newEnabled) {
            val activeIndex = runBlocking { indexRepository.getActiveIndex() }
            if (activeIndex != null) {
                val run = runBlocking { indexRepository.getRun(activeIndex) }
                if (run != null) {
                    ragRenderer.renderToggleSuccess(run.id.toString(), run.strategy.name, run.totalChunks)
                } else {
                    ragRenderer.renderToggleWarningNoIndex()
                }
            } else {
                ragRenderer.renderToggleWarningNoIndex()
            }
        } else {
            ragRenderer.renderToggleOff()
        }

        return state.copy(ragState = newRagState)
    }

    private fun handleStatus(state: CliState): CliState {
        val activeIndex = runBlocking { indexRepository.getActiveIndex() }
        if (activeIndex != null) {
            val run = runBlocking { indexRepository.getRun(activeIndex) }
            if (run != null) {
                ragRenderer.renderStatusWithIndex(state.ragState, run.id.toString(), run.strategy.name, run.totalChunks)
            } else {
                ragRenderer.renderStatusNoIndex(state.ragState)
            }
        } else {
            ragRenderer.renderStatusNoIndex(state.ragState)
        }
        return state
    }

    private fun handleList(state: CliState): CliState {
        val runs = runBlocking { indexRepository.getAllRuns() }
        val completedRuns = runs.filter { it.status == RunStatus.COMPLETED }
        val activeIndex = runBlocking { indexRepository.getActiveIndex() }

        if (completedRuns.isEmpty()) {
            ragRenderer.renderListEmpty()
        } else {
            ragRenderer.renderList(completedRuns, activeIndex)
        }
        return state
    }

    // ──── Новые команды Task 3 ────

    private fun handleSetMode(command: RagCommand.SetMode, state: CliState): CliState {
        val oldMode = state.ragState.config.mode
        val newState = configService.setMode(state.ragState, command.mode)
        analysisRenderer.renderModeChanged(oldMode, command.mode)
        return state.copy(ragState = newState)
    }

    private fun handleSetThreshold(command: RagCommand.SetThreshold, state: CliState): CliState {
        try {
            val newState = configService.setThreshold(state.ragState, command.threshold)
            analysisRenderer.renderThresholdChanged(command.threshold)
            return state.copy(ragState = newState)
        } catch (e: IllegalArgumentException) {
            println("\\u001b[31m✗\\u001b[0m ${e.message}")
            return state
        }
    }

    private fun handleSetTopK(command: RagCommand.SetTopK, state: CliState): CliState {
        try {
            val newState = configService.setTopK(state.ragState, command.initial, command.final)
            analysisRenderer.renderTopKChanged(command.initial, command.final)
            return state.copy(ragState = newState)
        } catch (e: IllegalArgumentException) {
            println("\\u001b[31m✗\\u001b[0m ${e.message}")
            return state
        }
    }

    private fun handleConfig(state: CliState): CliState {
        analysisRenderer.renderConfig(state.ragState.config)
        return state
    }

    private fun handleHistory(command: RagCommand.History, state: CliState): CliState {
        if (historyService == null) {
            println("\u001b[33m⚠\u001b[0m История запросов недоступна (SearchPipeline не настроен).")
            return state
        }
        val entries = runBlocking { historyService.getLast(command.limit) }
        historyRenderer.renderHistory(entries)
        return state
    }

    private fun handleHistoryDetail(command: RagCommand.HistoryDetail, state: CliState): CliState {
        if (historyService == null) {
            println("\u001b[33m⚠\u001b[0m История запросов недоступна.")
            return state
        }
        val entry = runBlocking { historyService.getDetailed(command.id) }
        if (entry != null) {
            historyRenderer.renderHistoryDetail(entry)
        } else {
            historyRenderer.renderHistoryNotFound(command.id)
        }
        return state
    }

    private fun handleAnalyze(state: CliState): CliState {
        if (metricsAnalyzer == null || historyService == null) {
            println("\u001b[33m⚠\u001b[0m Аналитика недоступна (SearchPipeline не настроен).")
            return state
        }
        val report = runBlocking { metricsAnalyzer.analyze() }
        analysisRenderer.renderAnalysis(report)
        return state
    }

    private fun handleCompare(command: RagCommand.Compare, state: CliState): CliState {
        if (metricsAnalyzer == null || historyService == null) {
            println("\u001b[33m⚠\u001b[0m Аналитика недоступна.")
            return state
        }
        val comparison = runBlocking { metricsAnalyzer.compareModes(command.mode1, command.mode2) }
        analysisRenderer.renderComparison(comparison)
        return state
    }

    private fun handleHistoryClear(state: CliState): CliState {
        if (historyService == null) {
            println("\u001b[33m⚠\u001b[0m История запросов недоступна.")
            return state
        }
        val count = runBlocking { historyService.count() }
        runBlocking { historyService.clearHistory() }
        historyRenderer.renderHistoryCleared(count)
        return state
    }
}
