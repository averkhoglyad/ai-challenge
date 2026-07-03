package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.DomainToolCall
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPTool
import java.time.Instant

/**
 * Port (интерфейс) для взаимодействия domain-слоя с LLM-инфраструктурой.
 *
 * Определяет контракт, который реализуется в infrastructure-слое.
 * Domain services зависят только от этого интерфейса, а не от конкретных
 * реализаций [io.averkhogliad.ai.challenge.utils.llm.LlmClient].
 *
 * Принцип инверсии зависимостей (DIP): domain определяет интерфейс,
 * infrastructure его реализует.
 */
interface LlmPort {

    /**
     * Отправляет одиночный промпт модели.
     *
     * @param prompt промпт пользователя
     * @param config конфигурация выполнения (temperature, maxTokens, modelId и др.)
     * @return результат выполнения: [TaskResult.Success], [TaskResult.Error] или [TaskResult.Partial]
     */
    suspend fun chat(prompt: Prompt, config: TaskExecutionConfig, tools: List<MCPTool>? = null): TaskResult

    /**
     * Отправляет последовательность сообщений модели (chat history).
     *
     * Позволяет задавать system prompt, контекст диалога и few-shot примеры.
     *
     * @param messages список сообщений (system, user, assistant)
     * @param config конфигурация выполнения
     * @return результат выполнения
     */
    suspend fun chatWithMessages(
        messages: List<ChatMessage>,
        config: TaskExecutionConfig,
        tools: List<MCPTool>? = null
    ): TaskResult

    /**
     * Возвращает список доступных моделей.
     *
     * @return список [ModelId] доступных моделей или пустой список, если API не поддерживает
     */
    suspend fun listModels(): List<io.averkhogliad.ai.challenge.week4.cli.domain.ModelId>
}

/**
 * Роль отправителя сообщения в чате.
 *
 * Фиксированный набор ролей, исключающий опору доменной логики
 * на строковые литералы "system"/"user"/"assistant".
 */
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    /** Infrastructure-friendly строковое представление роли (lowercase). */
    val roleName: String get() = name.lowercase()
}

/**
 * Domain-представление сообщения чата.
 *
 * Аналог [io.averkhogliad.ai.challenge.utils.llm.ChatMessage], но определён
 * в domain-слое для соблюдения принципа чистой архитектуры: domain не зависит
 * от utils/infrastructure.
 *
 * @property role роль отправителя
 * @property content текстовое содержимое сообщения
 * @property createdAt время создания сообщения
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val createdAt: Instant = Instant.now(),
    val toolCallId: String? = null,
    val toolCalls: List<DomainToolCall>? = null
) {
    companion object {
        /** Создаёт системное сообщение для установки контекста и инструкций модели. */
        fun system(content: String) = ChatMessage(ChatRole.SYSTEM, content, Instant.now())

        /** Создаёт пользовательское сообщение (промпт). */
        fun user(content: String) = ChatMessage(ChatRole.USER, content, Instant.now())

        /** Создаёт сообщение от ассистента (используется в few-shot примерах). */
        fun assistant(content: String) = ChatMessage(ChatRole.ASSISTANT, content, Instant.now())

        /** Создаёт сообщение от ассистента с вызовами инструментов. */
        fun assistantWithToolCalls(content: String, toolCalls: List<DomainToolCall>) =
            ChatMessage(ChatRole.ASSISTANT, content, Instant.now(), toolCalls = toolCalls)

        /** Создаёт сообщение с результатом выполнения инструмента. */
        fun tool(toolCallId: String, content: String) =
            ChatMessage(ChatRole.TOOL, content, Instant.now(), toolCallId = toolCallId)
    }
}
