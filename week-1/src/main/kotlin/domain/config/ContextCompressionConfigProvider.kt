package io.averkhogliad.ai.challenge.week1.domain.config

import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe провайдер конфигурации сжатия контекста с возможностью
 * динамического изменения параметров во время выполнения.
 *
 * Использует [AtomicReference] для обеспечения атомарности read-modify-write
 * операций. Все мутирующие операции используют [AtomicReference.updateAndGet]
 * для гарантированной потокобезопасности.
 *
 * ## Использование
 * ```kotlin
 * val provider = ContextCompressionConfigProvider(
 *     ContextCompressionConfig.fromProperties(props)
 * )
 * provider.setEnabled(true)
 * provider.setWindowSize(15)
 * val current = provider.get()
 * ```
 *
 * @property initialConfig начальная конфигурация, создаваемая при старте приложения
 */
class ContextCompressionConfigProvider(initialConfig: ContextCompressionConfig) {

    private val configRef = AtomicReference(initialConfig)

    /**
     * Возвращает текущую конфигурацию сжатия контекста.
     * Потокобезопасно благодаря [AtomicReference].
     */
    fun get(): ContextCompressionConfig = configRef.get()

    /**
     * Включает или выключает сжатие контекста.
     *
     * @param enabled `true` для включения, `false` для выключения
     */
    fun setEnabled(enabled: Boolean) {
        configRef.updateAndGet { it.copy(enabled = enabled) }
    }

    /**
     * Устанавливает размер скользящего окна (N).
     * Валидация происходит в конструкторе [ContextCompressionConfig].
     *
     * @param size новый размер окна, должен быть > 0 и >= текущего [blockSize][ContextCompressionConfig.blockSize]
     */
    fun setWindowSize(size: Int) {
        configRef.updateAndGet { current ->
            ContextCompressionConfig(
                enabled = current.enabled,
                windowSize = size,
                blockSize = current.blockSize,
                summaryModelId = current.summaryModelId
            )
        }
    }

    /**
     * Устанавливает размер блока для суммаризации (K).
     * Валидация происходит в конструкторе [ContextCompressionConfig].
     *
     * @param size новый размер блока, должен быть > 0 и <= текущего [windowSize][ContextCompressionConfig.windowSize]
     */
    fun setBlockSize(size: Int) {
        configRef.updateAndGet { current ->
            ContextCompressionConfig(
                enabled = current.enabled,
                windowSize = current.windowSize,
                blockSize = size,
                summaryModelId = current.summaryModelId
            )
        }
    }
}
