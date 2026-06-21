package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.*

/**
 * Иерархия состояний стратегий управления контекстом.
 *
 * Каждая стратегия, имеющая внутреннее состояние, представлена своим sealed-вариантом.
 * Stateless-стратегии используют [SlidingWindowState].
 *
 * Использование sealed interface гарантирует:
 * - Исчерпываемость when-выражений
 * - Типобезопасную передачу состояния между стратегиями
 * - Возможность расширения без изменения клиентского кода
 *
 * @property BranchingState состояние стратегии ветвления
 * @property StickyFactsState состояние стратегии sticky facts
 * @property SlidingWindowState маркер для stateless стратегии
 */
sealed interface StrategyState {

    /**
     * Состояние стратегии [BranchingStrategy].
     *
     * Хранит текущую активную ветку, историю чекпоинтов и все ветки диалога.
     *
     * @property currentBranch текущая активная ветка диалога
     * @property checkpoints история всех чекпоинтов
     * @property branches все ветки диалога (ключ — BranchId)
     */
    data class BranchingState(
        val currentBranch: DialogBranch,
        val checkpoints: List<Checkpoint>,
        val branches: Map<BranchId, DialogBranch>
    ) : StrategyState {
        companion object {
            /**
             * Создаёт начальное состояние с главной веткой.
             *
             * @param dialogId идентификатор диалога, для которого создаётся состояние
             * @return начальное состояние с единственной активной главной веткой
             */
            fun createInitial(dialogId: DialogId): BranchingState {
                val mainBranch = DialogBranch.createMain(dialogId)
                return BranchingState(
                    currentBranch = mainBranch,
                    checkpoints = emptyList(),
                    branches = mapOf(mainBranch.id to mainBranch)
                )
            }
        }
    }

    /**
     * Состояние стратегии [StickyFactsStrategy].
     *
     * Хранит извлечённые из диалога факты в виде [FactsStore].
     *
     * @property factsStore хранилище фактов (key-value memory)
     */
    data class StickyFactsState(
        val factsStore: FactsStore
    ) : StrategyState {
        companion object {
            /**
             * Создаёт начальное состояние с пустым хранилищем фактов.
             */
            fun createInitial(): StickyFactsState =
                StickyFactsState(factsStore = FactsStore())
        }
    }

    /**
     * Состояние стратегии [SlidingWindowStrategy].
     *
     * Sliding Window не имеет внутреннего состояния — вся логика определяется
     * конфигурацией и текущим содержимым диалога. Этот объект служит маркером
     * для единообразной передачи StrategyState.
     */
    data object SlidingWindowState : StrategyState
}
