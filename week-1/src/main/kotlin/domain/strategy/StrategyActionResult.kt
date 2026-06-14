package io.averkhogliad.ai.challenge.week1.domain.strategy

/**
 * Результат действия стратегии при обработке сообщения пользователя.
 *
 * Содержит информацию о том, какие действия были выполнены стратегией
 * (извлечение фактов, создание чекпоинта и т.д.).
 *
 * @property actionsPerformed список выполненных действий
 * @property metadata дополнительные метаданные (например, извлечённые факты)
 */
data class StrategyActionResult(
    val actionsPerformed: List<StrategyAction> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Создаёт пустой результат (для стратегий, которые не выполняют действий).
         */
        fun empty(): StrategyActionResult = StrategyActionResult()

        /**
         * Создаёт результат с одним действием.
         */
        fun withAction(action: StrategyAction): StrategyActionResult =
            StrategyActionResult(actionsPerformed = listOf(action))
    }
}

/**
 * Действие, выполненное стратегией.
 */
sealed interface StrategyAction {
    /** Извлечение фактов из сообщения */
    data class FactsExtracted(val factsCount: Int) : StrategyAction

    /** Создание чекпоинта */
    data class CheckpointCreated(val checkpointId: String) : StrategyAction

    /** Создание ветки */
    data class BranchCreated(val branchName: String) : StrategyAction

    /** Переключение на другую ветку */
    data class BranchSwitched(val branchName: String) : StrategyAction

    /** Обновление фактов */
    data class FactsUpdated(val factsCount: Int) : StrategyAction
}
