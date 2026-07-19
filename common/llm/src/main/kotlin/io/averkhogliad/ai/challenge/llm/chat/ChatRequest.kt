package io.averkhogliad.ai.challenge.llm.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Запрос к OpenAI-совместимому Chat Completion API.
 *
 * @property model Идентификатор модели (например, "gpt-4", "minimax/minimax-m3")
 * @property messages Список сообщений в диалоге
 * @property temperature Контролирует случайность генерации (0.0 - 2.0)
 * @property maxTokens Максимальное количество токенов в ответе
 * @property stop Список стоп-последовательностей
 * @property responseFormat Формат ответа (text или json_object)
 */
@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    @SerialName("response_format")
    val responseFormat: ChatParameters.ResponseFormat? = null,
    val tools: List<JsonObject>? = null
) {
    companion object {
        /**
         * Создает запрос из списка сообщений и параметров.
         */
        fun create(
            model: String,
            messages: List<ChatMessage>,
            parameters: ChatParameters = ChatParameters.DEFAULT,
            tools: List<JsonObject>? = null
        ): ChatRequest = ChatRequest(
            model = model,
            messages = messages,
            temperature = parameters.temperature,
            maxTokens = parameters.maxTokens,
            stop = parameters.stop,
            responseFormat = parameters.responseFormat,
            tools = tools
        )
    }
}
