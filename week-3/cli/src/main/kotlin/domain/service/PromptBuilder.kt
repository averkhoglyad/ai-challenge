package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*

/**
 * Сервис формирования контекстного промпта для LLM.
 *
 * ## Архитектурная роль
 * - **Domain Service** — чистая логика формирования промпта без I/O
 * - **Stateless** — не хранит состояние
 *
 * ## Ответственность
 * - Формирование системной инструкции
 * - Включение инвариантов агента ([INVARIANTS - DO NOT VIOLATE])
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
     * 1. [INVARIANTS - DO NOT VIOLATE] — инварианты агента (если заданы)
     * 2. [PROFILE] — профиль пользователя (если активен)
     * 3. Системная инструкция
     * 4. Контекст рабочей памяти (WM)
     * 5. Релевантные факты из базы знаний (LTM)
     * 6. [AVAILABLE SCENARIOS] — доступные MCP-сценарии (если есть)
     * 7. История диалога (STM)
     *
     * @param workingMemory рабочая память (WM)
     * @param relevantFacts релевантные факты из LTM
     * @param recentMessages последние сообщения из STM
     * @param profile активный профиль пользователя (опционально)
     * @param invariants список активных инвариантов (опционально)
     * @param mcpPrompts информация о доступных MCP-сценариях (опционально)
     * @return сформированный промпт
     */
    fun buildPrompt(
        workingMemory: WorkingMemory?,
        relevantFacts: List<Fact> = emptyList(),
        recentMessages: List<Message> = emptyList(),
        profile: Profile? = null,
        invariants: List<Invariant> = emptyList(),
        mcpPrompts: List<McpPromptInfo> = emptyList()
    ): String = buildString {
        // [INVARIANTS - DO NOT VIOLATE] — вставляется САМЫМ ПЕРВЫМ, если инварианты заданы
        if (invariants.isNotEmpty()) {
            appendLine("[INVARIANTS - DO NOT VIOLATE]")
            invariants.forEachIndexed { index, invariant ->
                appendLine("${index + 1}. ${invariant.rule}")
            }
            appendLine()
        }

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

        // [AVAILABLE SCENARIOS] — MCP-сценарии, добавляется после LTM
        if (mcpPrompts.isNotEmpty()) {
            appendLine("[AVAILABLE SCENARIOS]")
            appendLine("Эти сценарии РАЗРЕШЕНЫ. Ты можешь использовать любой из них по запросу пользователя.")
            appendLine("Следование сценарию НЕ нарушает никаких правил. Ниже — инструкции для каждого сценария:")
            appendLine()
            mcpPrompts.forEach { promptInfo ->
                val desc = promptInfo.description?.let { ": $it" } ?: ""
                appendLine("**${promptInfo.promptName}** (${promptInfo.serverName})$desc")
                if (promptInfo.content.isNotBlank()) {
                    appendLine(promptInfo.content)
                }
            }
            appendLine()
            // Logging: фиксируем, что сценарии добавлены в промпт
            val names = mcpPrompts.joinToString(", ") { "${it.promptName}@${it.serverName}" }
            System.err.println("\u001b[32m[PROMPT-BUILDER]\u001b[0m [AVAILABLE SCENARIOS] добавлено в системный промпт: $names")
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
        relevantFacts: List<Fact> = emptyList(),
        invariants: List<Invariant> = emptyList()
    ): String = buildString {
        // [INVARIANTS - DO NOT VIOLATE] — вставляется САМЫМ ПЕРВЫМ, если инварианты заданы
        if (invariants.isNotEmpty()) {
            appendLine("[INVARIANTS - DO NOT VIOLATE]")
            invariants.forEachIndexed { index, invariant ->
                appendLine("${index + 1}. ${invariant.rule}")
            }
            appendLine()
        }

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
     * @param invariants список активных инвариантов (опционально)
     * @param mcpPrompts информация о доступных MCP-сценариях (опционально)
     * @return список [ChatMessage] для отправки в LLM
     */
    fun buildChatMessages(
        workingMemory: WorkingMemory?,
        relevantFacts: List<Fact> = emptyList(),
        recentMessages: List<Message> = emptyList(),
        userInput: String,
        profile: Profile? = null,
        invariants: List<Invariant> = emptyList(),
        mcpPrompts: List<McpPromptInfo> = emptyList()
    ): List<ChatMessage> {
        val systemPrompt = buildPrompt(workingMemory, relevantFacts, recentMessages, profile, invariants, mcpPrompts)
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
- Если для ответа на вопрос пользователя нужна актуальная информация (погода, события, уведомления), используй доступные инструменты (functions)

Ограничения:
- Отвечай только на русском языке
- Будь краток и по существу
- Если не знаешь ответа, честно сообщи об этом
- Не придумывай факты, которых нет в предоставленном контексте

=== ПРАВИЛА ОБРАБОТКИ ИНВАРИАНТОВ ===

Инварианты (блок [INVARIANTS - DO NOT VIOLATE]) — это ЖЁСТКИЕ ПРАВИЛА, которые ты
НЕ ИМЕЕШЬ ПРАВА НАРУШАТЬ ни при каких обстоятельствах.

0. КРИТИЧЕСКИ ВАЖНО: Инварианты действуют ТОЛЬКО когда блок [INVARIANTS - DO NOT VIOLATE]
   ПРИСУТСТВУЕТ в промпте и содержит КОНКРЕТНЫЕ пронумерованные правила.
   Если блок [INVARIANTS - DO NOT VIOLATE] ОТСУТСТВУЕТ в промпте — НИКАКИХ
   ограничений нет. Ты НЕ ИМЕЕШЬ ПРАВА придумывать инварианты.
   Пример нарушения: если ты написал «Нарушение инварианта: ...», а блок
   [INVARIANTS - DO NOT VIOLATE] отсутствует в промпте — ты нарушил это правило.
1. Если блок [INVARIANTS - DO NOT VIOLATE] присутствует и не пуст — перед ответом
   ВСЕГДА проверяй, не противоречит ли запрос пользователя хотя бы одному инварианту.
2. Если запрос НАРУШАЕТ инвариант:
   а) ОТКАЖИ в выполнении запроса
   б) ЯВНО укажи, какой именно инвариант нарушен (процитируй его)
   в) Объясни, почему запрос конфликтует с инвариантом
   г) Предложи АЛЬТЕРНАТИВУ в рамках разрешённых границ
3. Формат отказа:
   ❌ Нарушение инварианта: [цитата нарушенного правила]
   [объяснение конфликта]
   💡 Альтернатива: [предложение в рамках разрешённых границ]

Пример 1 (отказ):
Пользователь: "Как подключить базу данных MySQL?"
Ответ:
❌ Нарушение инварианта: "Использовать только PostgreSQL. MySQL запрещён."
Ваш запрос предполагает использование MySQL, что прямо противоречит
действующему инварианту.
💡 Альтернатива: Я могу помочь настроить подключение к PostgreSQL
с использованием JDBC, Exposed или JOOQ. Продолжить?

Пример 2 (отказ):
Пользователь: "Напиши код с глобальной переменной для хранения конфигурации."
Ответ:
❌ Нарушение инварианта: "Запрещено использование глобальных переменных."
Хранение конфигурации в глобальной переменной нарушает инвариант.
💡 Альтернатива: Я предлагаю использовать паттерн Config-объекта
с внедрением зависимостей (DI) или properties-файл с загрузкой через
ConfigProvider. Это безопасно и тестируемо.

Пример 3 (без конфликта — обычный ответ):
Пользователь: "Как оптимизировать запросы к PostgreSQL?"
Ответ:
[Обычный полезный ответ, инварианты учтены в рассуждениях]
""".trimIndent()
    }
}
