package io.averkhogliad.ai.challenge.llm.chat

/**
 * Ответ от LLM API.
 *
 * @property content Текстовое содержимое ответа модели (может быть null при tool_calls)
 * @property finishReason Причина завершения генерации:
 *                        - "stop" - модель завершила ответ естественным образом
 *                        - "length" - достигнут лимит max_tokens
 *                        - "content_filter" - ответ заблокирован фильтром контента
 *                        - "tool_calls" - модель запросила вызов инструментов
 *                        - null - причина не указана
 * @property usage Информация об использовании токенов
 * @property toolCalls Список запрошенных вызовов инструментов
 */
data class ChatResponse(
    val content: String?,
    val finishReason: String?,
    val usage: Usage?,
    val toolCalls: List<ToolCall>? = null
) {
    /**
     * Статистика использования токенов.
     *
     * @property promptTokens Количество токенов в запросе (промпт + system message)
     * @property completionTokens Количество токенов в ответе модели
     * @property totalTokens Общее количество токенов (promptTokens + completionTokens)
     */
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )

    /**
     * Проверяет, был ли ответ завершен из-за достижения лимита токенов.
     */
    fun isTruncated(): Boolean = finishReason == "length"

    /**
     * Проверяет, был ли ответ заблокирован фильтром контента.
     */
    fun isFiltered(): Boolean = finishReason == "content_filter"

    /**
     * Проверяет, запросила ли модель вызов инструментов.
     */
    fun hasToolCalls(): Boolean = !toolCalls.isNullOrEmpty()
}
