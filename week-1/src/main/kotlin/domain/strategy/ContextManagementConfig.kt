package io.averkhogliad.ai.challenge.week1.domain.strategy

/**
 * Тип стратегии управления контекстом.
 */
enum class StrategyType(val code: String) {
    SLIDING_WINDOW("sliding-window"),
    STICKY_FACTS("sticky-facts"),
    BRANCHING("branching");

    companion object {
        fun fromCode(code: String): StrategyType =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown strategy code: $code")
    }
}

/**
 * Конфигурация управления контекстом для всех стратегий.
 *
 * @property currentStrategy текущая активная стратегия
 * @property slidingWindow конфигурация Sliding Window
 * @property stickyFacts конфигурация Sticky Facts
 * @property branching конфигурация Branching
 */
data class ContextManagementConfig(
    val currentStrategy: StrategyType = StrategyType.SLIDING_WINDOW,
    val slidingWindow: SlidingWindowConfig = SlidingWindowConfig(),
    val stickyFacts: StickyFactsConfig = StickyFactsConfig(),
    val branching: BranchingConfig = BranchingConfig()
) {
    companion object {
        fun fromProperties(properties: Map<String, String>): ContextManagementConfig {
            val strategyCode = properties["context.strategy.default"] ?: "sliding-window"
            val strategy = try {
                StrategyType.fromCode(strategyCode)
            } catch (_: Exception) {
                StrategyType.SLIDING_WINDOW
            }
            return ContextManagementConfig(
                currentStrategy = strategy,
                slidingWindow = SlidingWindowConfig(
                    windowSize = properties["context.strategy.sliding-window.window-size"]?.toIntOrNull() ?: 10
                ),
                stickyFacts = StickyFactsConfig(
                    windowSize = properties["context.strategy.sticky-facts.window-size"]?.toIntOrNull() ?: 5,
                    maxFacts = properties["context.strategy.sticky-facts.max-facts"]?.toIntOrNull() ?: 20,
                    extractionModelId = properties["context.strategy.sticky-facts.extraction-model"]
                ),
                branching = BranchingConfig(
                    checkpointInterval = properties["context.strategy.branching.checkpoint-interval"]?.toIntOrNull()
                        ?: 5,
                    autoDetectTopicChange = properties["context.strategy.branching.auto-detect-topic-change"]?.toBooleanStrictOrNull()
                        ?: true
                )
            )
        }
    }
}

/**
 * Конфигурация стратегии Sliding Window.
 *
 * @property windowSize количество последних сообщений, сохраняемых в контексте
 */
data class SlidingWindowConfig(
    val windowSize: Int = 10
) {
    init {
        require(windowSize > 0) { "windowSize must be positive, got $windowSize" }
    }
}

/**
 * Конфигурация стратегии Sticky Facts.
 *
 * @property windowSize количество последних сообщений, передаваемых вместе с фактами
 * @property maxFacts максимальное количество хранимых фактов
 * @property extractionModelId опциональный ID модели для извлечения фактов
 */
data class StickyFactsConfig(
    val windowSize: Int = 5,
    val maxFacts: Int = 20,
    val extractionModelId: String? = null
) {
    init {
        require(windowSize > 0) { "windowSize must be positive, got $windowSize" }
        require(maxFacts > 0) { "maxFacts must be positive, got $maxFacts" }
    }
}

/**
 * Конфигурация стратегии Branching.
 *
 * @property checkpointInterval интервал сообщений для автоматических чекпоинтов
 * @property autoDetectTopicChange автоматически детектировать смену темы
 */
data class BranchingConfig(
    val checkpointInterval: Int = 5,
    val autoDetectTopicChange: Boolean = true
) {
    init {
        require(checkpointInterval > 0) { "checkpointInterval must be positive, got $checkpointInterval" }
    }
}
