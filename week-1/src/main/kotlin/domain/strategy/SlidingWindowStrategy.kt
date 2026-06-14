package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContext
import io.averkhogliad.ai.challenge.week1.domain.context.SlidingWindowCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage

/**
 * Стратегия скользящего окна (Sliding Window).
 *
 * Хранит только последние N сообщений, полностью отбрасывая более ранние.
 * Использует существующий [SlidingWindowCompressor] для сжатия контекста.
 *
 * ## Принцип работы
 * 1. При обработке сообщения: ничего не делает (сообщение уже добавлено в диалог)
 * 2. При подготовке контекста: вызывает [SlidingWindowCompressor.compress] для получения
 *    последних N сообщений
 *
 * ## Преимущества
 * - Простота реализации
 * - Минимальный расход токенов
 * - Предсказуемое поведение
 *
 * ## Недостатки
 * - Потеря контекста старых сообщений
 * - Нет сохранения важных деталей
 *
 * @property compressor существующий компрессор для переиспользования
 */
class SlidingWindowStrategy(
    private val compressor: SlidingWindowCompressor
) : ContextManagementStrategy {

    override val name: String = "Sliding Window"
    override val description: String = "Хранит только последние N сообщений, отбрасывая старые. " +
            "Простая и эффективная для коротких диалогов."

    override suspend fun processUserMessage(
        dialog: Dialog,
        userMessage: String,
        config: ContextManagementConfig
    ): StrategyActionResult {
        // Sliding Window не выполняет дополнительных действий при обработке сообщения
        return StrategyActionResult.empty()
    }

    override suspend fun prepareContext(
        dialog: Dialog,
        systemPrompt: String,
        config: ContextManagementConfig
    ): PreparedContext {
        val slidingConfig = config.slidingWindow

        // Создаём конфигурацию компрессии для SlidingWindowCompressor
        val compressionConfig = ContextCompressionConfig(
            enabled = true,
            windowSize = slidingConfig.windowSize,
            blockSize = slidingConfig.windowSize // blockSize = windowSize для простого отбрасывания
        )

        // Вызываем существующий компрессор
        val dialogContext: DialogContext = compressor.compress(
            messages = dialog.messages,
            config = compressionConfig,
            previousSummary = null // Не используем суммаризацию, просто отбрасываем
        )

        // Преобразуем DialogContext в PreparedContext
        val messages = dialogContext.toMessagesList(systemPrompt)
        val chatMessages = messages.map { utilsMsg ->
            ChatMessage(
                role = io.averkhogliad.ai.challenge.week1.domain.service.ChatRole.valueOf(
                    utilsMsg.role.uppercase()
                ),
                content = utilsMsg.content
            )
        }

        return PreparedContext.fromMessages(
            messages = chatMessages,
            metadata = mapOf(
                "strategy" to "sliding-window",
                "windowSize" to slidingConfig.windowSize,
                "compressedMessageCount" to dialogContext.compressedMessageCount
            )
        )
    }
}
