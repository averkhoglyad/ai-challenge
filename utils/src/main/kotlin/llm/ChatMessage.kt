package io.averkhogliad.ai.challenge.utils.llm

import kotlinx.serialization.Serializable

/**
 * Сообщение в чате для отправки в LLM API.
 *
 * @property role Роль отправителя: "system", "user" или "assistant"
 * @property content Текстовое содержимое сообщения
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        /**
         * Создает системное сообщение для установки контекста и инструкций модели.
         */
        fun system(content: String) = ChatMessage("system", content)
        
        /**
         * Создает пользовательское сообщение (промпт).
         */
        fun user(content: String) = ChatMessage("user", content)
        
        /**
         * Создает сообщение от ассистента (используется в few-shot примерах).
         */
        fun assistant(content: String) = ChatMessage("assistant", content)
    }
}
