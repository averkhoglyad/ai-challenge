package io.averkhogliad.ai.challenge.week4.cli.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.RunStatus
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import kotlinx.coroutines.runBlocking

/**
 * Обработчик RAG-команд.
 *
 * Каждый метод принимает команду и текущее состояние, возвращает обновлённое состояние.
 * Вызовы suspend-функций репозитория оборачиваются в [runBlocking],
 * следуя паттерну существующих обработчиков CLI.
 *
 * @param indexRepository репозиторий индексов для получения информации об индексах
 * @param renderer рендерер результатов RAG-команд
 */
class RagCommandHandler(
    private val indexRepository: IndexRepository,
    private val renderer: RagCommandRenderer
) {

    /**
     * Диспетчеризует [RagCommand] и возвращает обновлённое состояние.
     */
    fun handle(command: RagCommand, state: CliState): CliState = when (command) {
        is RagCommand.Toggle -> handleToggle(state)
        is RagCommand.Status -> handleStatus(state)
        is RagCommand.List -> handleList(state)
    }

    /**
     * Переключает RAG on/off.
     *
     * Toggle работает всегда, независимо от наличия активного индекса.
     * При включении без индекса — warning.
     */
    private fun handleToggle(state: CliState): CliState {
        val newEnabled = !state.ragState.enabled
        val newRagState = state.ragState.copy(enabled = newEnabled)

        if (newEnabled) {
            val activeIndex = runBlocking { indexRepository.getActiveIndex() }
            if (activeIndex != null) {
                val run = runBlocking { indexRepository.getRun(activeIndex) }
                if (run != null) {
                    renderer.renderToggleSuccess(run.id.toString(), run.strategy.name, run.totalChunks)
                } else {
                    renderer.renderToggleWarningNoIndex()
                }
            } else {
                renderer.renderToggleWarningNoIndex()
            }
        } else {
            renderer.renderToggleOff()
        }

        return state.copy(ragState = newRagState)
    }

    /**
     * Показывает текущее состояние RAG.
     */
    private fun handleStatus(state: CliState): CliState {
        val activeIndex = runBlocking { indexRepository.getActiveIndex() }
        if (activeIndex != null) {
            val run = runBlocking { indexRepository.getRun(activeIndex) }
            if (run != null) {
                renderer.renderStatusWithIndex(state.ragState, run.id.toString(), run.strategy.name, run.totalChunks)
            } else {
                renderer.renderStatusNoIndex(state.ragState)
            }
        } else {
            renderer.renderStatusNoIndex(state.ragState)
        }
        return state
    }

    /**
     * Показывает список доступных индексов (completed runs).
     */
    private fun handleList(state: CliState): CliState {
        val runs = runBlocking { indexRepository.getAllRuns() }
        val completedRuns = runs.filter { it.status == RunStatus.COMPLETED }
        val activeIndex = runBlocking { indexRepository.getActiveIndex() }

        if (completedRuns.isEmpty()) {
            renderer.renderListEmpty()
        } else {
            renderer.renderList(completedRuns, activeIndex)
        }
        return state
    }
}
