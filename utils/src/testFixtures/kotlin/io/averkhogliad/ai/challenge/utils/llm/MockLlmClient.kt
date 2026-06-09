package io.averkhogliad.ai.challenge.utils.llm

/**
 * Мок-реализация [LlmClient] для тестов.
 *
 * Возвращает предопределённые ответы без реальных HTTP-запросов.
 * Поведение настраивается через параметры конструктора:
 *
 * ```kotlin
 * // Simple default
 * val client = MockLlmClient()
 *
 * // Настраиваемый ответ на chat
 * val client = MockLlmClient(
 *     chatResponse = ChatResponse("Hello!", "stop", ChatResponse.Usage(5, 10, 15))
 * )
 *
 * // Кастомная логика через лямбды
 * val client = MockLlmClient(
 *     onChat = { prompt, _, _, _ -> ChatResponse("Echo: $prompt", "stop", null) }
 * )
 * ```
 *
 * @property chatResponse Ответ, возвращаемый методом [chat] (если [onChat] не задан)
 * @property chatWithMessagesResponse Ответ, возвращаемый методом [chatWithMessages] (если [onChatWithMessages] не задан)
 * @property onChat Лямбда для кастомного поведения [chat]
 * @property onChatWithMessages Лямбда для кастомного поведения [chatWithMessages]
 */
class MockLlmClient(
    private val chatResponse: ChatResponse = ChatResponse("test", "stop", null),
    private val chatWithMessagesResponse: ChatResponse = ChatResponse("test", "stop", null),
    private val onChat: (suspend (String, String?, ChatParameters, String?) -> ChatResponse)? = null,
    private val onChatWithMessages: (suspend (List<ChatMessage>, ChatParameters, String?) -> ChatResponse)? = null,
) : LlmClient {

    override suspend fun chat(
        prompt: String,
        systemPrompt: String?,
        parameters: ChatParameters,
        model: String?,
    ): ChatResponse {
        return onChat?.invoke(prompt, systemPrompt, parameters, model) ?: chatResponse
    }

    override suspend fun chatWithMessages(
        messages: List<ChatMessage>,
        parameters: ChatParameters,
        model: String?,
    ): ChatResponse {
        return onChatWithMessages?.invoke(messages, parameters, model) ?: chatWithMessagesResponse
    }

    override fun close() {}
}
