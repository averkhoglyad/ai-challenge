package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Интерфейс управления runtime-состоянием RAG-сессии.
 *
 * Позволяет CLI-слою переопределять параметры конфигурации
 * (например, порог релевантности) без изменения domain-конфига [RagConfig].
 *
 * Реализация должна быть thread-safe для использования в REPL-цикле.
 */
interface RagStateManager {
    /** Получить текущее состояние RAG-сессии */
    fun getState(): RagSessionState

    /** Установить порог релевантности (runtime-переопределение) */
    fun updateRelevanceThreshold(threshold: Float)

    /** Сбросить все runtime-переопределения к значениям из конфигурации */
    fun resetToDefaults()
}

/**
 * Реализация [RagStateManager] на основе [MutableStateFlow] для thread-safe обновлений.
 *
 * Использует [RagConfig] как источник значений по умолчанию для сброса.
 *
 * @property config доменная конфигурация RAG с дефолтными значениями
 * @property stateHolder реактивный контейнер состояния RAG-сессии
 */
class DefaultRagStateManager(
    private val config: RagConfig,
    private val stateHolder: MutableStateFlow<RagSessionState>
) : RagStateManager {

    override fun getState(): RagSessionState = stateHolder.value

    override fun updateRelevanceThreshold(threshold: Float) {
        stateHolder.update { it.copy(relevanceThreshold = threshold) }
    }

    override fun resetToDefaults() {
        stateHolder.update { it.copy(relevanceThreshold = config.relevanceThreshold) }
    }
}
