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
 * ## Приоритет ключей конфигурации
 *
 * Для параметров SlidingWindow используется каскадный приоритет:
 * 1. `context.compression.window-size` / `context.compression.block-size` —
 *    общие ключи сжатия контекста (Task 4 + Task 5). **Имеют высший приоритет.**
 * 2. `context.strategy.sliding-window.window-size` — специфичный ключ стратегии
 *    (legacy, оставлен для обратной совместимости). Используется как fallback.
 *
 * Это означает, что если заданы оба ключа (`context.compression.window-size`
 * и `context.strategy.sliding-window.window-size`), победит общий ключ
 * `context.compression.window-size`. Такое решение принято для унификации
 * конфигурации между Task 4 и Task 5.
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
                    windowSize = properties["context.compression.window-size"]?.toIntOrNull()
                        ?: properties["context.strategy.sliding-window.window-size"]?.toIntOrNull()
                        ?: 10,
                    blockSize = properties["context.compression.block-size"]?.toIntOrNull() ?: 5,
                    summaryModelId = properties["context.compression.summary-model-id"]
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
 * Синхронизирована с [io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig]
 * для единообразного поведения сжатия контекста в Task 4 и Task 5.
 *
 * Feature-флаг `enabled` намеренно отсутствует: стратегия скользящего окна ВСЕГДА включает компрессию.
 * Для Task 4 `enabled` управляется через [ContextCompressionConfigProvider] на уровне executor'а.
 *
 * @property windowSize количество последних сообщений, сохраняемых в контексте
 * @property blockSize количество сообщений для суммаризации за один вызов LLM
 * @property summaryModelId опциональный ID модели для суммаризации; если null — используется модель по умолчанию
 */
data class SlidingWindowConfig(
    val windowSize: Int = 10,
    val blockSize: Int = 5,
    val summaryModelId: String? = null
) {
    init {
        require(windowSize > 0) { "windowSize must be positive, got $windowSize" }
        require(blockSize > 0) { "blockSize must be positive, got $blockSize" }
        require(blockSize <= windowSize) { "blockSize ($blockSize) must be <= windowSize ($windowSize)" }
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
