package io.averkhogliad.ai.challenge.week0.domain.service

import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig

/**
 * Mock-реализация [LlmPort] для тестирования domain services.
 *
 * Позволяет:
 * - Задавать предопределённые ответы (success/error) для конкретных промптов
 * - Проверять, какие вызовы были сделаны (spy-функциональность)
 * - Настраивать поведение через [respondWith] и [respondWithError]
 *
 * Пример использования:
 * ```kotlin
 * val mockLlmPort = MockLlmPort()
 * mockLlmPort.respondWith { prompt, config ->
 *     TaskResult.Success("Ответ на: ${prompt.value}")
 * }
 * ```
 */
class MockLlmPort : LlmPort {

    /** Список всех зарегистрированных вызовов [chat]. */
    private val _chatCalls = mutableListOf<Pair<Prompt, TaskExecutionConfig>>()
    val chatCalls: List<Pair<Prompt, TaskExecutionConfig>> get() = _chatCalls.toList()

    /** Список всех зарегистрированных вызовов [chatWithMessages]. */
    private val _chatWithMessagesCalls = mutableListOf<Pair<List<ChatMessage>, TaskExecutionConfig>>()
    val chatWithMessagesCalls: List<Pair<List<ChatMessage>, TaskExecutionConfig>> get() = _chatWithMessagesCalls.toList()

    /** Функция, определяющая ответ на [chat]. */
    private var chatHandler: ((Prompt, TaskExecutionConfig) -> TaskResult)? = null

    /** Функция, определяющая ответ на [chatWithMessages]. */
    private var chatWithMessagesHandler: ((List<ChatMessage>, TaskExecutionConfig) -> TaskResult)? = null

    /** Флаг для симуляции ошибок. */
    private var throwOnNextCall: Throwable? = null

    /**
     * Настраивает обработчик ответа для [chat].
     */
    fun respondWith(handler: (Prompt, TaskExecutionConfig) -> TaskResult) {
        chatHandler = handler
    }

    /**
     * Настраивает фиксированный успешный ответ для [chat].
     */
    fun respondWithSuccess(content: String = "mock response") {
        chatHandler = { _, _ -> TaskResult.Success(content) }
    }

    /**
     * Настраивает фиксированный ошибочный ответ для [chat].
     */
    fun respondWithError(message: String = "mock error") {
        chatHandler = { _, _ -> TaskResult.Error(message) }
    }

    /**
     * Настраивает обработчик ответа для [chatWithMessages].
     */
    fun respondWithMessages(handler: (List<ChatMessage>, TaskExecutionConfig) -> TaskResult) {
        chatWithMessagesHandler = handler
    }

    /**
     * Настраивает фиксированный успешный ответ для [chatWithMessages].
     */
    fun respondWithMessagesSuccess(content: String = "mock response") {
        chatWithMessagesHandler = { _, _ -> TaskResult.Success(content) }
    }

    /**
     * Настраивает фиксированный ошибочный ответ для [chatWithMessages].
     */
    fun respondWithMessagesError(message: String = "mock error") {
        chatWithMessagesHandler = { _, _ -> TaskResult.Error(message) }
    }

    /**
     * Настраивает выброс исключения на следующем вызове.
     */
    fun throwOnNextCall(throwable: Throwable) {
        throwOnNextCall = throwable
    }

    override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        _chatCalls.add(prompt to config)

        throwOnNextCall?.let {
            throwOnNextCall = null
            throw it
        }

        return chatHandler?.invoke(prompt, config) ?: TaskResult.Success("default mock response")
    }

    override suspend fun chatWithMessages(
        messages: List<ChatMessage>,
        config: TaskExecutionConfig
    ): TaskResult {
        _chatWithMessagesCalls.add(messages to config)

        throwOnNextCall?.let {
            throwOnNextCall = null
            throw it
        }

        return chatWithMessagesHandler?.invoke(messages, config)
            ?: TaskResult.Success("default mock response")
    }

    /** Список моделей, возвращаемый методом [listModels]. */
    var availableModels: List<ModelId> = listOf(ModelId("mock-model"))

    override suspend fun listModels(): List<ModelId> = availableModels

    /** Сбрасывает все настройки и историю вызовов. */
    fun reset() {
        _chatCalls.clear()
        _chatWithMessagesCalls.clear()
        chatHandler = null
        chatWithMessagesHandler = null
        throwOnNextCall = null
        availableModels = listOf(ModelId("mock-model"))
    }
}
