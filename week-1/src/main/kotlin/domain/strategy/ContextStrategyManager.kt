package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.context.SlidingWindowCompressor
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort

/**
 * Менеджер стратегий управления контекстом.
 *
 * Управляет доступными стратегиями, текущей активной стратегией
 * и переключением между ними.
 *
 * @property llmPort порт для взаимодействия с LLM
 * @property slidingWindowCompressor компрессор для SlidingWindow стратегии
 */
class ContextStrategyManager(
    private val llmPort: LlmPort,
    private val slidingWindowCompressor: SlidingWindowCompressor
) {
    // Доступные стратегии
    private val strategies: Map<StrategyType, ContextManagementStrategy>

    // Текущая активная стратегия
    private var currentStrategyType: StrategyType = StrategyType.SLIDING_WINDOW

    init {
        val factsExtractor = FactsExtractor(llmPort)

        strategies = mapOf(
            StrategyType.SLIDING_WINDOW to SlidingWindowStrategy(slidingWindowCompressor),
            StrategyType.STICKY_FACTS to StickyFactsStrategy(factsExtractor),
            StrategyType.BRANCHING to BranchingStrategy {
                // Возвращаем конфигурацию по умолчанию
                BranchingConfig()
            }
        )
    }

    /**
     * Получает текущую активную стратегию.
     */
    fun getCurrentStrategy(): ContextManagementStrategy {
        return strategies[currentStrategyType]
            ?: throw IllegalStateException("No strategy found for type: $currentStrategyType")
    }

    /**
     * Получает тип текущей стратегии.
     */
    fun getCurrentStrategyType(): StrategyType = currentStrategyType

    /**
     * Переключает на указанную стратегию.
     * 
     * @param strategyType тип стратегии для активации
     * @throws IllegalArgumentException если стратегия не найдена
     */
    fun switchStrategy(strategyType: StrategyType) {
        if (!strategies.containsKey(strategyType)) {
            throw IllegalArgumentException("Strategy not found: $strategyType")
        }
        currentStrategyType = strategyType
    }

    /**
     * Переключает на стратегию по индексу (для интерактивного меню).
     * 
     * @param index индекс стратегии (1-based)
     * @throws IllegalArgumentException если индекс вне диапазона
     */
    fun switchStrategyByIndex(index: Int) {
        val types = StrategyType.entries
        if (index < 1 || index > types.size) {
            throw IllegalArgumentException("Invalid strategy index: $index. Must be 1-${types.size}")
        }
        currentStrategyType = types[index - 1]
    }

    /**
     * Получает список всех доступных стратегий с их описаниями.
     */
    fun listStrategies(): List<StrategyInfo> {
        return strategies.map { (type, strategy) ->
            StrategyInfo(
                type = type,
                name = strategy.name,
                description = strategy.description,
                isCurrent = type == currentStrategyType
            )
        }
    }

    /**
     * Получает стратегию по типу.
     */
    fun getStrategy(type: StrategyType): ContextManagementStrategy {
        return strategies[type]
            ?: throw IllegalArgumentException("Strategy not found: $type")
    }

    /**
     * Информация о стратегии для отображения в меню.
     */
    data class StrategyInfo(
        val type: StrategyType,
        val name: String,
        val description: String,
        val isCurrent: Boolean
    )
}
