package io.averkhogliad.ai.challenge.utils.llm

/**
 * Ответ от LLM API.
 *
 * @property content Текстовое содержимое ответа модели
 * @property finishReason Причина завершения генерации:
 *                        - "stop" - модель завершила ответ естественным образом
 *                        - "length" - достигнут лимит max_tokens
 *                        - "content_filter" - ответ заблокирован фильтром контента
 *                        - null - причина не указана
 * @property usage Информация об использовании токенов
 */
data class ChatResponse(
    val content: String,
    val finishReason: String?,
    val usage: Usage?
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
}
