package io.averkhogliad.ai.challenge.week1.domain.context

import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole

/**
 * Построитель промптов для суммаризации истории диалога.
 *
 * Предоставляет структурированные промпты на английском языке (для лучшего качества LLM)
 * для двух сценариев: первичная суммаризация блока сообщений и инкрементальное обновление
 * существующего summary.
 *
 * Промпты инструктируют LLM сохранять:
 * - Ключевые факты и утверждения
 * - Сущности (люди, места, объекты) и их атрибуты
 * - Принятые решения и их обоснования
 * - Контекст, необходимый для понимания последующего диалога
 * - Хронологию важных событий
 */
object SummaryPromptBuilder {

    /**
     * Строит промпт для первичной суммаризации блока сообщений.
     *
     * Используется при первом запуске сжатия, когда previousSummary == null.
     * Инструктирует LLM создать структурированное summary, сохраняющее
     * всю критически важную информацию из диалога.
     *
     * @param messages блок сообщений для суммаризации (в хронологическом порядке)
     * @return текст промпта для отправки в LLM
     */
    fun buildInitialSummaryPrompt(messages: List<ChatMessage>): String {
        val conversation = formatConversation(messages)
        return """
            |You are a precise conversation summarizer. Your task is to compress the following conversation fragment into a concise yet comprehensive summary.
            |
            |## Instructions
            |1. **Preserve key facts**: Include all factual claims, data points, and concrete information mentioned.
            |2. **Retain entities**: List all people, places, organizations, products, or other named entities with their attributes.
            |3. **Capture decisions**: Record any decisions made, conclusions reached, or agreements formed, including the reasoning behind them.
            |4. **Maintain context**: Include contextual information necessary to understand the flow of conversation and the intent behind messages.
            |5. **Chronology**: Preserve the temporal order of important events and topic shifts.
            |6. **Be concise**: Aim for 30-40% of the original length while keeping all critical information.
            |7. **Format**: Use bullet points for distinct topics. Group related information together.
            |
            |## Output Format
            |Provide only the summary text — no preamble, no meta-commentary, no "Here is the summary:".
            |
            |## Conversation to Summarize
            |$conversation
            |
            |## Summary
        """.trimMargin()
    }

    /**
     * Строит промпт для инкрементального обновления существующего summary.
     *
     * Используется при повторных вызовах сжатия, когда previousSummary не null.
     * Инструктирует LLM интегрировать новую информацию из новых сообщений
     * в существующее summary без потери уже сохранённых данных.
     *
     * @param existingSummary существующее summary от предыдущих вызовов сжатия
     * @param newMessages новые сообщения для интеграции (в хронологическом порядке)
     * @return текст промпта для отправки в LLM
     */
    fun buildIncrementalSummaryPrompt(existingSummary: String, newMessages: List<ChatMessage>): String {
        val conversation = formatConversation(newMessages)
        return """
            |You are a precise conversation summarizer. Below is an existing summary of an earlier part of the conversation, followed by new messages that occurred after that summary was created.
            |
            |Your task is to produce an **updated** summary that integrates the new information into the existing summary.
            |
            |## Existing Summary
            |$existingSummary
            |
            |## New Messages to Integrate
            |$conversation
            |
            |## Instructions
            |1. **Integrate, don't replace**: The existing summary contains valuable context. Merge new facts, entities, decisions, and context into it.
            |2. **Resolve contradictions**: If new messages contradict the existing summary, prioritize the new information and note the update.
            |3. **Preserve chronology**: Maintain the temporal order of events. Add new events at the appropriate position.
            |4. **Keep structure**: Maintain the summary's structure (bullet points, grouping). Add new sections if new topics emerge.
            |5. **Avoid redundancy**: Do not duplicate information already captured. Only add genuinely new or updated information.
            |6. **Be concise**: The combined summary should not exceed 40% of what the full conversation text would be.
            |
            |## Output Format
            |Provide only the updated summary text — no preamble, no meta-commentary, no "Here is the updated summary:".
            |
            |## Updated Summary
        """.trimMargin()
    }

    /**
     * Форматирует список сообщений в человекочитаемый текст для включения в промпт.
     *
     * @param messages сообщения для форматирования
     * @return отформатированный текст диалога
     */
    private fun formatConversation(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return "(no messages)"

        return messages.joinToString("\n") { message ->
            val roleLabel = when (message.role) {
                ChatRole.USER -> "User"
                ChatRole.ASSISTANT -> "Assistant"
                ChatRole.SYSTEM -> "System"
            }
            "$roleLabel: ${message.content}"
        }
    }
}
