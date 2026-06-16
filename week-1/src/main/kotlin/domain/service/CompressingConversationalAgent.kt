package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.model.MessageTag

/**
 * Агент с поддержкой сжатия контекста диалога.
 *
 * Декоратор над [ConversationalAgent], который добавляет механизм сжатия
 * истории сообщений через [DialogContextCompressor]. При превышении размера
 * окна старые сообщения суммаризируются в [Dialog.accumulatedSummary],
 * что позволяет поддерживать длинные диалоги без превышения лимитов контекста LLM.
 *
 * ## Архитектурная роль
 * - **Decorator Pattern** — расширяет [ConversationalAgent] функциональностью сжатия
 * - **Domain Service** — инкапсулирует бизнес-логику сжатия контекста
 * - **Stateless** — не хранит состояние, принимает [DialogId] как параметр
 *
 * ## Логика работы
 * 1. Проверяет [ContextCompressionConfigProvider.get] — если `enabled == false`, делегирует напрямую
 * 2. Загружает диалог по [dialogId] из репозитория
 * 3. Добавляет user message в историю диалога
 * 4. Вызывает [DialogContextCompressor.compress] с полной историей и текущим summary
 * 5. Формирует messages list из [DialogContext] (summary + recent messages)
 * 6. Вызывает LLM через [LlmPort.chatWithMessages]
 * 7. Обновляет [Dialog.accumulatedSummary] в диалоге
 * 8. Сохраняет обновлённый диалог в репозиторий
 * 9. Возвращает [TaskResult] с телеметрией
 *
 * @property delegate базовый агент для делегирования (без сжатия)
 * @property compressor стратегия сжатия контекста
 * @property configProvider провайдер конфигурации сжатия
 * @property dialogRepository репозиторий для персистентного хранения диалогов
 * @property llmPort порт для взаимодействия с LLM
 * @property systemPrompt опциональный system prompt для установки контекста
 */
class CompressingConversationalAgent(
    private val delegate: ConversationalAgent,
    private val compressor: DialogContextCompressor,
    private val configProvider: ContextCompressionConfigProvider,
    private val dialogRepository: DialogRepository,
    private val llmPort: LlmPort,
    private val systemPrompt: String? = null
) : Agent {

    /**
     * Обрабатывает пользовательский запрос в контексте указанного диалога.
     *
     * Если сжатие отключено — полностью делегирует [delegate] без overhead.
     * Иначе применяет механизм сжатия контекста перед отправкой в LLM.
     *
     * @param prompt пользовательский промпт
     * @param config конфигурация выполнения (temperature, maxTokens, modelId и др.)
     * @param dialogId идентификатор диалога, в котором обрабатывается запрос (обязателен)
     * @return результат выполнения: [TaskResult.Success], [TaskResult.Error] или [TaskResult.Partial]
     * @throws IllegalArgumentException если [dialogId] не указан
     */
    override suspend fun process(prompt: Prompt, config: TaskExecutionConfig, dialogId: DialogId?): TaskResult {
        requireNotNull(dialogId) { "CompressingConversationalAgent requires dialogId parameter" }

        val compressionConfig = configProvider.get()

        // Если сжатие отключено — делегируем напрямую без overhead
        if (!compressionConfig.enabled) {
            return delegate.process(prompt, config, dialogId)
        }

        return try {
            // 1. Загрузить диалог
            var dialog = dialogRepository.findById(dialogId)
                ?: Dialog.create(dialogId, "Dialog ${dialogId.value.take(8)}")

            // 2. Добавить user message в диалог
            dialog = dialog.addUserMessage(prompt.value)

            // 3. Получить все messages из диалога + accumulatedSummary
            val messages = dialog.messages
            val previousSummary = dialog.accumulatedSummary

            // 4. Вызвать compressor.compress(messages, config, accumulatedSummary)
            val dialogContext = compressor.compress(messages, compressionConfig, previousSummary)

            // 5. Сформировать messages list из DialogContext
            val compressedUtilsMessages = dialogContext.toMessagesList(systemPrompt ?: "")
            // Конвертировать utils.ChatMessage в domain.ChatMessage
            val compressedMessages = compressedUtilsMessages.map { utilsMsg ->
                ChatMessage(
                    role = ChatRole.valueOf(utilsMsg.role.uppercase()),
                    content = utilsMsg.content
                )
            }

            // 6. Вызвать LLM через llmPort
            val result = llmPort.chatWithMessages(compressedMessages, config)

            // 7. Обновить сomppression state в диалоге и сохранить
            // Помечаем сжатые сообщения тегом Compressed
            val compressedCount = dialogContext.compressedMessageCount
            if (compressedCount > 0) {
                val compressedIndices = (0 until compressedCount).toList().toIntArray()
                dialog = dialog.tagMessages(MessageTag.Compressed, *compressedIndices)
            }

            when (result) {
                is TaskResult.Success -> {
                    // Сохраняем token usage в историю диалога
                    result.tokenUsage?.let { dialog = dialog.addTokenUsage(it) }
                    // Обновляем accumulatedSummary из контекста сжатия
                    // compressedMessageCount теперь вычисляется автоматически из messageTags
                    dialogContext.summary?.let { dialog = dialog.copy(accumulatedSummary = it) }
                    // Добавляем assistant response в диалог
                    dialog = dialog.addAssistantMessage(result.content)
                    dialogRepository.save(dialog)
                }

                is TaskResult.Partial -> {
                    dialogContext.summary?.let { dialog = dialog.copy(accumulatedSummary = it) }
                    dialog = dialog.addAssistantMessage(result.content)
                    dialogRepository.save(dialog)
                }

                is TaskResult.Error -> {
                    // При ошибке всё равно сохраняем диалог с user message и обновлённым summary
                    dialogContext.summary?.let { dialog = dialog.copy(accumulatedSummary = it) }
                    dialogRepository.save(dialog)
                }
            }

            // 8. Вернуть TaskResult с телеметрией
            result
        } catch (e: Exception) {
            TaskResult.Error(
                message = "CompressingConversationalAgent failed: ${e.message}",
                cause = e
            )
        }
    }
}
