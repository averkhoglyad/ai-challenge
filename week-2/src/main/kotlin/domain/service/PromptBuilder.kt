package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.*

/**
 * Сервис формирования контекстного промпта для LLM.
 *
 * ## Архитектурная роль
 * - **Domain Service** — чистая логика формирования промпта без I/O
 * - **Stateless** — не хранит состояние
 *
 * ## Ответственность
 * - Формирование системной инструкции
 * - Включение профиля пользователя ([PROFILE])
 * - Включение контекста рабочей памяти (WM)
 * - Включение релевантных фактов из базы знаний (LTM)
 * - Включение истории диалога (STM)
 */
class PromptBuilder {

    /**
     * Формирует системный промпт из контекста памяти.
     *
     * Структура промпта:
     * 1. [PROFILE] — профиль пользователя (если активен)
     * 2. Системная инструкция
     * 3. Контекст рабочей памяти (WM)
     * 4. Релевантные факты из базы знаний (LTM)
     * 5. История диалога (STM)
     *
     * @param workingMemory рабочая память (WM)
     * @param relevantFacts релевантные факты из LTM
     * @param recentMessages последние сообщения из STM
     * @param profile активный профиль пользователя (опционально)
     * @return сформированный промпт
     */
    fun buildPrompt(
        workingMemory: WorkingMemory?,
        relevantFacts: List<Fact> = emptyList(),
        recentMessages: List<Message> = emptyList(),
        profile: Profile? = null
    ): String = buildString {
        // [PROFILE] — вставляется ПЕРЕД системной инструкцией, если профиль активен
        if (profile != null) {
            appendLine("[PROFILE]")
            appendLine("Name: ${profile.name}")
            appendLine("Description: ${profile.description}")
            appendLine("Instructions: ${profile.instructions}")
            appendLine()
        }

        appendLine(SYSTEM_INSTRUCTION)
        appendLine()

        if (workingMemory != null) {
            val wmContext = workingMemory.toPromptContext()
            if (wmContext.isNotBlank()) {
                appendLine("=== Контекст рабочей памяти (WM) ===")
                appendLine(wmContext)
                appendLine()
            }
        }

        if (relevantFacts.isNotEmpty()) {
            appendLine("=== Релевантные факты из базы знаний (LTM) ===")
            relevantFacts.forEachIndexed { index, fact ->
                appendLine("${index + 1}. ${fact.content}")
            }
            appendLine()
        }

        if (recentMessages.isNotEmpty()) {
            appendLine("=== История диалога (STM) ===")
            recentMessages.forEach { message ->
                val roleLabel = when (message.role) {
                    MessageRole.USER -> "Пользователь"
                    MessageRole.ASSISTANT -> "Ассистент"
                    MessageRole.SYSTEM -> "Система"
                }
                appendLine("[$roleLabel] ${message.content}")
            }
            appendLine()
        }
    }

    /**
     * Формирует промпт для команды `:plan` — запроса шагов у LLM.
     *
     * @param taskTitle название задачи
     * @param taskDescription описание задачи (опционально)
     * @param workingMemory рабочая память
     * @param relevantFacts релевантные факты из LTM
     * @return промпт для генерации шагов
     */
    fun buildPlanPrompt(
        taskTitle: String,
        taskDescription: String? = null,
        workingMemory: WorkingMemory? = null,
        relevantFacts: List<Fact> = emptyList()
    ): String = buildString {
        appendLine("Проанализируй задачу и предложи план выполнения в виде списка шагов.")
        appendLine()
        appendLine("Задача: $taskTitle")
        if (!taskDescription.isNullOrBlank()) {
            appendLine("Описание: $taskDescription")
        }
        appendLine()

        if (workingMemory != null) {
            val wmContext = workingMemory.toPromptContext()
            if (wmContext.isNotBlank()) {
                appendLine("Контекст:")
                appendLine(wmContext)
                appendLine()
            }
        }

        if (relevantFacts.isNotEmpty()) {
            appendLine("Релевантные факты:")
            relevantFacts.forEachIndexed { index, fact ->
                appendLine("${index + 1}. ${fact.content}")
            }
            appendLine()
        }

        appendLine("Формат ответа (только список шагов, каждый на новой строке):")
        appendLine("1. Шаг 1")
        appendLine("2. Шаг 2")
        appendLine("...")
    }

    /**
     * Формирует список сообщений для отправки в LLM через chatWithMessages.
     *
     * @param workingMemory рабочая память (WM)
     * @param relevantFacts релевантные факты из LTM
     * @param recentMessages последние сообщения из STM
     * @param userInput ввод пользователя
     * @param profile активный профиль пользователя (опционально)
     * @return список [ChatMessage] для отправки в LLM
     */
    fun buildChatMessages(
        workingMemory: WorkingMemory?,
        relevantFacts: List<Fact> = emptyList(),
        recentMessages: List<Message> = emptyList(),
        userInput: String,
        profile: Profile? = null
    ): List<ChatMessage> {
        val systemPrompt = buildPrompt(workingMemory, relevantFacts, recentMessages, profile)
        val messages = mutableListOf<ChatMessage>()

        // Системное сообщение
        messages.add(ChatMessage.system(systemPrompt))

        // История диалога
        recentMessages.forEach { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> ChatRole.USER
                MessageRole.ASSISTANT -> ChatRole.ASSISTANT
                MessageRole.SYSTEM -> ChatRole.SYSTEM
            }
            messages.add(ChatMessage(role, msg.content, msg.timestamp))
        }

        // Сообщение пользователя
        messages.add(ChatMessage.user(userInput))

        return messages
    }

    companion object {
        /** Системная инструкция для LLM */
        val SYSTEM_INSTRUCTION = """
Ты — AI-ассистент для управления задачами. Твоя роль — помогать пользователю 
с планированием, анализом и выполнением задач.

Твои возможности:
- Отвечай на вопросы пользователя, используя предоставленный контекст
- Помогай анализировать задачи и предлагать шаги для их выполнения
- Учитывай историю диалога для поддержания связного разговора
- Используй факты из базы знаний, если они релевантны запросу

Ограничения:
- Отвечай только на русском языке
- Будь краток и по существу
- Если не знаешь ответа, честно сообщи об этом
- Не придумывай факты, которых нет в предоставленном контексте
""".trimIndent()
    }
}
