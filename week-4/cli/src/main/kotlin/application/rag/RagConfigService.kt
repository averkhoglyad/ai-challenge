package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode

/**
 * Сервис управления конфигурацией RAG-поиска.
 *
 * Все методы возвращают новый иммутабельный [RagSessionState] через [copy].
 * Валидация параметров делегируется [SearchConfig.init].
 */
class RagConfigService {

    /** Установить режим поиска */
    fun setMode(state: RagSessionState, mode: SearchMode): RagSessionState {
        val newConfig = state.config.copy(mode = mode)
        return state.copy(config = newConfig)
    }

    /** Установить порог фильтрации (0.0–1.0) */
    fun setThreshold(state: RagSessionState, threshold: Float): RagSessionState {
        val newConfig = state.config.copy(threshold = threshold)
        return state.copy(config = newConfig)
    }

    /** Установить top-K initial и final */
    fun setTopK(state: RagSessionState, initial: Int, final: Int): RagSessionState {
        val newConfig = state.config.copy(topKInitial = initial, topKFinal = final)
        return state.copy(config = newConfig)
    }

    /** Получить текущую конфигурацию */
    fun getConfig(state: RagSessionState): SearchConfig = state.config
}
