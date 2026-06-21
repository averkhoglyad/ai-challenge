package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort

/**
 * Фабрика для создания стратегий управления контекстом.
 *
 * Инкапсулирует знание о том, какие зависимости нужны каждой стратегии,
 * и предоставляет готовую карту стратегий для [ContextStrategyManager].
 *
 * ## Принцип работы
 * - Каждая реализация фабрики создаёт полный набор стратегий
 * - [ContextStrategyManager] использует фабрику для инициализации
 * - Позволяет заменять реализации стратегий без изменения менеджера
 *
 * @see DefaultStrategyFactory
 */
interface StrategyFactory {

    /**
     * Создаёт карту стратегий (тип → стратегия).
     *
     * Все стратегии создаются с корректными зависимостями.
     *
     * @return неизменяемая карта: ключ — [StrategyType], значение — готовая стратегия
     */
    fun createStrategies(): Map<StrategyType, ContextManagementStrategy>
}

/**
 * Стандартная реализация [StrategyFactory], создающая production-стратегии.
 *
 * Создаёт:
 * - [SlidingWindowStrategy] с компрессором и провайдером конфигурации
 * - [StickyFactsStrategy] с экстрактором фактов на основе LLM
 * - [BranchingStrategy] с провайдером конфигурации и детектором смены темы
 *
 * @property llmPort порт для взаимодействия с LLM (используется FactsExtractor)
 * @property compressor компрессор контекста для SlidingWindow
 * @property configProvider опциональный провайдер динамической конфигурации сжатия
 */
class DefaultStrategyFactory(
    private val llmPort: LlmPort,
    private val compressor: DialogContextCompressor,
    private val configProvider: ContextCompressionConfigProvider?
) : StrategyFactory {

    override fun createStrategies(): Map<StrategyType, ContextManagementStrategy> {
        val factsExtractor = FactsExtractor(llmPort)

        return mapOf(
            StrategyType.SLIDING_WINDOW to SlidingWindowStrategy(compressor, configProvider),
            StrategyType.STICKY_FACTS to StickyFactsStrategy(factsExtractor),
            StrategyType.BRANCHING to BranchingStrategy(
                configProvider = { BranchingConfig() },
                topicChangeDetector = TopicChangeDetector()
            )
        )
    }
}
