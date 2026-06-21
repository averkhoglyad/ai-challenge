package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContext
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
import kotlinx.coroutines.withTimeoutOrNull

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
 *      с ключом [StrategyMetadataKeys.NEW_ACCUMULATED_SUMMARY].
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
 * ## Обработка ошибок
 * При ошибке компрессора используется graceful degradation: возвращаются последние N
 * сообщений без сжатия, а ошибка записывается в metadata под ключом `"compressionError"`.
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
        config: ContextManagementConfig,
        state: StrategyState?
    ): StrategyActionResult {
        require(userMessage.isNotBlank()) { "userMessage cannot be blank" }
        // Sliding Window не выполняет дополнительных действий при обработке сообщения
        return StrategyActionResult.empty()
    }

    override suspend fun prepareContext(
        dialog: Dialog,
        systemPrompt: String,
        config: ContextManagementConfig,
        state: StrategyState?
    ): PreparedContext {
        val slidingConfig = config.slidingWindow

        // Приоритет: динамический configProvider > статический ContextManagementConfig.
        val dynamicConfig = configProvider?.get()
        val effectiveWindowSize = dynamicConfig?.windowSize ?: slidingConfig.windowSize
        val effectiveBlockSize = dynamicConfig?.blockSize ?: slidingConfig.blockSize
        val effectiveSummaryModelId = dynamicConfig?.summaryModelId ?: slidingConfig.summaryModelId

        // Если сообщений недостаточно — возвращаем полный контекст без сжатия
        if (dialog.messages.size <= effectiveWindowSize) {
            return PreparedContext.fromMessages(
                messages = listOf(ChatMessage.system(systemPrompt)) + dialog.messages,
                metadata = mapOf(
                    StrategyMetadataKeys.STRATEGY to "sliding-window",
                    StrategyMetadataKeys.WINDOW_SIZE to effectiveWindowSize,
                    StrategyMetadataKeys.COMPRESSED_MESSAGE_COUNT to 0,
                    StrategyMetadataKeys.NEW_ACCUMULATED_SUMMARY to ""
                )
            )
        }

        // Компрессия всегда включена для стратегии скользящего окна
        val compressionConfig = ContextCompressionConfig(
            enabled = true,
            windowSize = effectiveWindowSize,
            blockSize = effectiveBlockSize,
            summaryModelId = effectiveSummaryModelId
        )

        val timeoutMs = config.timeouts.compressionTimeoutMs

        // Вызываем компрессор с таймаутом и обработкой ошибок (graceful degradation)
        val dialogContext: DialogContext? = withTimeoutOrNull(timeoutMs) {
            try {
                compressor.compress(
                    messages = dialog.messages,
                    config = compressionConfig,
                    previousSummary = dialog.accumulatedSummary
                )
            } catch (e: Exception) {
                null
            }
        }

        if (dialogContext == null) {
            // Fallback: возвращаем последние N сообщений без сжатия
            val fallbackMessages = listOf(ChatMessage.system(systemPrompt)) +
                    dialog.messages.takeLast(effectiveWindowSize)
            return PreparedContext.fromMessages(
                messages = fallbackMessages,
                metadata = mapOf(
                    StrategyMetadataKeys.STRATEGY to "sliding-window",
                    StrategyMetadataKeys.WINDOW_SIZE to effectiveWindowSize,
                    StrategyMetadataKeys.COMPRESSED_MESSAGE_COUNT to 0,
                    StrategyMetadataKeys.NEW_ACCUMULATED_SUMMARY to "",
                    "compressionError" to "Timeout or error during compression"
                )
            )
        }

        // Преобразуем DialogContext в PreparedContext с безопасным преобразованием ролей
        val messages = dialogContext.toMessagesList(systemPrompt)
        val chatMessages = messages.mapNotNull { utilsMsg ->
            val role = try {
                ChatRole.valueOf(utilsMsg.role.uppercase())
            } catch (e: IllegalArgumentException) {
                null // Пропускаем сообщения с неизвестными ролями
            }
            role?.let { ChatMessage(role = it, content = utilsMsg.content) }
        }

        return PreparedContext.fromMessages(
            messages = chatMessages,
            metadata = mapOf(
                StrategyMetadataKeys.STRATEGY to "sliding-window",
                StrategyMetadataKeys.WINDOW_SIZE to effectiveWindowSize,
                StrategyMetadataKeys.BLOCK_SIZE to effectiveBlockSize,
                StrategyMetadataKeys.COMPRESSED_MESSAGE_COUNT to dialogContext.compressedMessageCount,
                StrategyMetadataKeys.NEW_ACCUMULATED_SUMMARY to (dialogContext.summary ?: "")
            )
        )
    }
}
