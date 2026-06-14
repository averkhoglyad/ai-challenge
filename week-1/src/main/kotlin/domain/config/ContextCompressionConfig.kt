package io.averkhogliad.ai.challenge.week1.domain.config

/**
 * Immutable конфигурация сжатия контекста диалога.
 *
 * Определяет параметры скользящего окна и блочной суммаризации,
 * используемые [io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor].
 *
 * ## Алгоритм сжатия (Task 4)
 * - **windowSize (N)** — размер скользящего окна: количество последних сообщений,
 *   которые сохраняются в несжатом виде
 * - **blockSize (K)** — размер блока для суммаризации: каждые K сообщений,
 *   выходящих за пределы окна, сжимаются в summary
 * - Когда сообщений становится > N, первые K из них суммаризируются,
 *   и окно сдвигается
 *
 * @property enabled флаг включения сжатия контекста
 * @property windowSize размер скользящего окна (N), должен быть > 0
 * @property blockSize размер блока для суммаризации (K), должен быть > 0 и <= windowSize
 * @property summaryModelId опциональный ID модели для суммаризации; если null — используется модель по умолчанию
 */
data class ContextCompressionConfig(
    val enabled: Boolean = false,
    val windowSize: Int = 10,
    val blockSize: Int = 5,
    val summaryModelId: String? = null
) {
    init {
        require(windowSize > 0) { "windowSize must be positive, got $windowSize" }
        require(blockSize > 0) { "blockSize must be positive, got $blockSize" }
        require(blockSize <= windowSize) { "blockSize ($blockSize) must be <= windowSize ($windowSize)" }
    }

    companion object {
        /**
         * Создаёт [ContextCompressionConfig] из flat-словаря свойств
         * (например, из application.properties).
         *
         * Поддерживаемые ключи:
         * - `context.compression.enabled` (boolean)
         * - `context.compression.window-size` (int)
         * - `context.compression.block-size` (int)
         * - `context.compression.summary-model-id` (string, optional)
         *
         * @param properties словарь ключ-значение из конфигурации
         * @return валидный экземпляр [ContextCompressionConfig]
         */
        fun fromProperties(properties: Map<String, String>): ContextCompressionConfig {
            val enabled = properties["context.compression.enabled"]?.toBooleanStrictOrNull() ?: false
            val windowSize = properties["context.compression.window-size"]?.toIntOrNull() ?: 10
            val blockSize = properties["context.compression.block-size"]?.toIntOrNull() ?: 5
            val summaryModelId = properties["context.compression.summary-model-id"]
            return ContextCompressionConfig(
                enabled = enabled,
                windowSize = windowSize,
                blockSize = blockSize,
                summaryModelId = summaryModelId
            )
        }
    }
}
