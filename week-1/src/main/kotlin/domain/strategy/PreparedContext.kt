package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage

/**
 * Подготовленный контекст для отправки в LLM.
 *
 * Содержит список сообщений и метаданные о том, как контекст был сформирован.
 *
 * @property messages список сообщений для отправки в LLM (включая system prompt)
 * @property estimatedTokens примерная оценка количества токенов
 * @property metadata дополнительные метаданные стратегии (факты, чекпоинты и т.д.)
 */
data class PreparedContext(
    val messages: List<ChatMessage>,
    val estimatedTokens: Int = 0,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Создаёт контекст из списка сообщений с автоматической оценкой токенов.
         */
        fun fromMessages(messages: List<ChatMessage>, metadata: Map<String, Any> = emptyMap()): PreparedContext {
            val totalChars = messages.sumOf { it.content.length }
            val estimatedTokens = if (totalChars == 0) 0 else maxOf(1, totalChars / 4)
            return PreparedContext(
                messages = messages,
                estimatedTokens = estimatedTokens,
                metadata = metadata
            )
        }
    }
}
