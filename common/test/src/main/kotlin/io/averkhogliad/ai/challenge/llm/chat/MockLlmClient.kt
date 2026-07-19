package io.averkhogliad.ai.challenge.llm.chat

import kotlinx.serialization.json.JsonObject

/**
 * Мок-реализация [LlmClient] для тестов.
 *
 * Возвращает предопределённые ответы без реальных HTTP-запросов.
 */
class MockLlmClient(
    private val chatResponse: ChatResponse = ChatResponse("test", "stop", null),
    private val chatWithMessagesResponse: ChatResponse = ChatResponse("test", "stop", null),
    private val onChat: (suspend (String, String?, ChatParameters, String?, List<JsonObject>?) -> ChatResponse)? = null,
    private val onChatWithMessages: (suspend (List<ChatMessage>, ChatParameters, String?, List<JsonObject>?) -> ChatResponse)? = null,
) : LlmClient {

    override suspend fun chat(
        prompt: String,
        systemPrompt: String?,
        parameters: ChatParameters,
        model: String?,
        tools: List<JsonObject>?,
    ): ChatResponse {
        return onChat?.invoke(prompt, systemPrompt, parameters, model, tools) ?: chatResponse
    }

    override suspend fun chatWithMessages(
        messages: List<ChatMessage>,
        parameters: ChatParameters,
        model: String?,
        tools: List<JsonObject>?,
    ): ChatResponse {
        return onChatWithMessages?.invoke(messages, parameters, model, tools) ?: chatWithMessagesResponse
    }

    override fun close() {}
}
