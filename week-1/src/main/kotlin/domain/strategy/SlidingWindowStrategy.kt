package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContext
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage

/**
 * Стратегия скользящего окна (Sliding Window).
 *
 * Хранит последние N сообщений в исходном виде, а более ранние сообщения
 * сжимает в накопительное summary ([Dialog.accumulatedSummary]) через
 * блочную суммаризацию. Использует [DialogContextCompressor]
 * для сжатия контекста.
 *
 * ## Принцип работы
 * 1. При обработке сообщения: ничего не делает (сообщение уже добавлено в диалог)
 * 2. При подготовке контекста:
 *    - Если сообщений <= windowSize: возвращает полный контекст без сжатия
 *    - Если сообщений > windowSize: вызывает [SlidingWindowCompressor.compress]
 *      с [Dialog.accumulatedSummary] в качестве previousSummary для инкрементальной
 *      суммаризации. Результат (новое summary) возвращается в metadata
 *      с ключом `"newAccumulatedSummary"`.
 *
 * ## Конфигурация
 * Приоритет параметров (по убыванию):
 * 1. [ContextCompressionConfigProvider.get()] — динамическая конфигурация
 *    (общая для Task 4 и Task 5)
 * 2. [SlidingWindowConfig] из [ContextManagementConfig.slidingWindow] —
 *    статическая конфигурация (fallback)
 *
 * Параметры:
 * - `windowSize` — количество последних сообщений, сохраняемых в исходном виде
 * - `blockSize` — размер блока для суммаризации (сообщения старше окна сжимаются блоками)
 * - `summaryModelId` — модель для суммаризации (null = модель по умолчанию)
 *
 * Feature-флаг `enabled` из [ContextCompressionConfig] намеренно игнорируется:
 * стратегия скользящего окна ВСЕГДА включает компрессию при превышении windowSize.
 *
 * ## Преимущества
 * - Простота реализации
 * - Предсказуемое поведение
 * - Инкрементальная суммаризация сохраняет контекст старых сообщений
 * - Контролируемый расход токенов через windowSize
 *
 * ## Недостатки
 * - Детали могут теряться при агрессивной суммаризации
 * - Дополнительные вызовы LLM для генерации summary
 *
 * @property compressor компрессор контекста (любая реализация [DialogContextCompressor])
 * @property configProvider опциональный провайдер динамической конфигурации сжатия
 */
class SlidingWindowStrategy(
    private val compressor: DialogContextCompressor,
    private val configProvider: ContextCompressionConfigProvider? = null
) : ContextManagementStrategy {

    override val name: String = "Sliding Window"
    override val description: String = "Sliding Window: хранит только последние N сообщений, " +
            "суммаризируя старые через накопительное сжатие. " +
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

        // Приоритет: динамический configProvider > статический ContextManagementConfig.
        // enabled НЕ берётся из configProvider — стратегия скользящего окна ВСЕГДА включает компрессию.
        // configProvider управляет только windowSize/blockSize/summaryModelId (общие для Task 4 и Task 5).
        // Захватываем один снимок конфигурации, чтобы избежать гонки данных при трёх вызовах get().
        val dynamicConfig = configProvider?.get()
        val effectiveWindowSize = dynamicConfig?.windowSize ?: slidingConfig.windowSize
        val effectiveBlockSize = dynamicConfig?.blockSize ?: slidingConfig.blockSize
        val effectiveSummaryModelId = dynamicConfig?.summaryModelId ?: slidingConfig.summaryModelId

        // Если сообщений недостаточно — возвращаем полный контекст без сжатия
        if (dialog.messages.size <= effectiveWindowSize) {
            return PreparedContext.fromMessages(
                messages = listOf(ChatMessage.system(systemPrompt)) + dialog.messages,
                metadata = mapOf(
                    "strategy" to "sliding-window",
                    "windowSize" to effectiveWindowSize,
                    "compressedMessageCount" to 0,
                    "newAccumulatedSummary" to ""
                )
            )
        }

        // Компрессия всегда включена для стратегии скользящего окна
        // (enabled = true — это суть стратегии; feature-флаг управляет только Task 4)
        val compressionConfig = ContextCompressionConfig(
            enabled = true,
            windowSize = effectiveWindowSize,
            blockSize = effectiveBlockSize,
            summaryModelId = effectiveSummaryModelId
        )

        // Вызываем компрессор с накопленным summary из диалога (инкрементальная суммаризация)
        val dialogContext: DialogContext = compressor.compress(
            messages = dialog.messages,
            config = compressionConfig,
            previousSummary = dialog.accumulatedSummary
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
                "windowSize" to effectiveWindowSize,
                "blockSize" to effectiveBlockSize,
                "compressedMessageCount" to dialogContext.compressedMessageCount,
                "newAccumulatedSummary" to (dialogContext.summary ?: "")
            )
        )
    }
}
