package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenUsage

/**
 * Агент с поддержкой персистентных диалогов и сбором телеметрии.
 *
 * Расширяет базовый [Agent] функциональностью сохранения истории сообщений,
 * управления множественными изолированными диалогами и автоматическим
 * сбором телеметрии использования токенов. Каждый диалог имеет
 * собственный контекст, который сохраняется в [DialogRepository].
 *
 * ## Архитектурная роль
 * - **Domain Service** — инкапсулирует бизнес-логику ведения диалога
 * - **Stateless** — не хранит состояние, принимает [DialogId] как параметр
 * - **Hexagonal elements** — зависит от port [DialogRepository]
 *
 * ## Логика работы
 * 1. Загружает диалог по [dialogId] из репозитория
 * 2. Добавляет user message в историю диалога
 * 3. Формирует messages list: [system prompt] + [история] + [новое user message]
 * 4. Вызывает [LlmPort.chatWithMessages] с полной историей
 * 5. Извлекает [TokenUsage] из [TaskResult.Success] и сохраняет в [Dialog.tokenUsageHistory]
 * 6. Добавляет assistant response в диалог
 * 7. Сохраняет обновлённый диалог в [DialogRepository]
 * 8. Возвращает [TaskResult] (с уже заполненным [TokenUsage] через авто-извлечение из metadata)
 *
 * @property llmPort порт для взаимодействия с LLM
 * @property repository репозиторий для персистентного хранения диалогов
 * @property systemPrompt опциональный system prompt для установки контекста
 */
class ConversationalAgent(
    private val llmPort: LlmPort,
    private val repository: DialogRepository,
    private val systemPrompt: String? = null
) : Agent {

    /**
     * Обрабатывает пользовательский запрос в контексте указанного диалога.
     *
     * Всегда собирает телеметрию использования токенов. Executor/CLI решает, отображать или нет.
     *
     * @param prompt пользовательский промпт
     * @param config конфигурация выполнения (temperature, maxTokens, modelId и др.)
     * @param dialogId идентификатор диалога, в котором обрабатывается запрос (обязателен для ConversationalAgent)
     * @return результат выполнения: [TaskResult.Success], [TaskResult.Error] или [TaskResult.Partial]
     * @throws IllegalArgumentException если [dialogId] не указан
     */
    override suspend fun process(prompt: Prompt, config: TaskExecutionConfig, dialogId: DialogId?): TaskResult {
        requireNotNull(dialogId) { "ConversationalAgent requires dialogId parameter" }
        return try {
            // 1. Загрузить диалог
            var dialog = repository.findById(dialogId)
                ?: Dialog.create(dialogId, "Dialog ${dialogId.value.take(8)}")

            // 2. Добавить user message
            dialog = dialog.addUserMessage(prompt.value)

            // 3. Сформировать messages list (system + history + new user message)
            val messages = buildMessagesList(dialog)

            // 4. Вызвать llmPort.chatWithMessages
            val result = llmPort.chatWithMessages(messages, config)

            // 5. Извлечь token usage и сохранить в историю диалога
            when (result) {
                is TaskResult.Success -> {
                    // Сохраняем token usage в историю диалога (если доступен)
                    result.tokenUsage?.let { dialog = dialog.addTokenUsage(it) }
                    dialog = dialog.addAssistantMessage(result.content)
                    repository.save(dialog)
                }

                is TaskResult.Partial -> {
                    dialog = dialog.addAssistantMessage(result.content)
                    repository.save(dialog)
                }

                is TaskResult.Error -> {
                    // При ошибке всё равно сохраняем диалог с user message
                    repository.save(dialog)
                }
            }

            // 6. Вернуть TaskResult (с авто-извлечённым TokenUsage)
            result
        } catch (e: Exception) {
            TaskResult.Error(
                message = "ConversationalAgent failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Формирует список сообщений для отправки в LLM.
     *
     * Порядок:
     * 1. System prompt (если задан)
     * 2. История диалога (все сообщения)
     *
     * @param dialog текущий диалог с историей
     * @return список сообщений для LLM
     */
    private fun buildMessagesList(dialog: Dialog): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // System prompt (если задан)
        if (systemPrompt != null) {
            messages.add(ChatMessage.system(systemPrompt))
        }

        // История диалога
        messages.addAll(dialog.messages)

        return messages
    }
}
