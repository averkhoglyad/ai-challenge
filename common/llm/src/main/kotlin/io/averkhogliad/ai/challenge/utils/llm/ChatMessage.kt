@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.averkhogliad.ai.challenge.utils.llm

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Сообщение в чате для отправки в LLM API.
 *
 * @property role Роль отправителя: "system", "user", "assistant" или "tool"
 * @property content Текстовое содержимое сообщения (может быть null при tool_calls)
 * @property toolCallId Идентификатор вызова инструмента (только для role="tool")
 * @property toolCalls Список вызовов инструментов от ассистента (только для role="assistant")
 */
@Serializable
data class ChatMessage(
    val role: String,
    @SerialName("content")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val content: String? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null
) {
    companion object {
        /**
         * Создает системное сообщение для установки контекста и инструкций модели.
         */
        fun system(content: String) = ChatMessage(role = "system", content = content)

        /**
         * Создает пользовательское сообщение (промпт).
         */
        fun user(content: String) = ChatMessage(role = "user", content = content)

        /**
         * Создает сообщение от ассистента (используется в few-shot примерах).
         */
        fun assistant(content: String) = ChatMessage(role = "assistant", content = content)

        /**
         * Создает сообщение с результатом выполнения инструмента.
         */
        fun tool(toolCallId: String, content: String) = ChatMessage(
            role = "tool",
            content = content,
            toolCallId = toolCallId
        )

        /**
         * Создает сообщение от ассистента с вызовами инструментов.
         */
        fun assistantWithToolCalls(content: String, toolCalls: List<ToolCall>) = ChatMessage(
            role = "assistant",
            content = content.ifBlank { null },
            toolCalls = toolCalls
        )
    }
}

/**
 * Вызов инструмента (tool call) от LLM.
 *
 * @property id Уникальный идентификатор вызова
 * @property type Тип вызова (всегда "function")
 * @property function Информация о вызываемой функции
 */
@Serializable
data class ToolCall(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "function",
    val function: FunctionCall
)

/**
 * Информация о вызываемой функции в tool call.
 *
 * @property name Имя функции (инструмента)
 * @property arguments Аргументы функции в виде JSON-строки
 */
@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String  // JSON-строка
)
