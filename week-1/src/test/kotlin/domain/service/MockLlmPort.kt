package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.ModelId
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig

/**
 * Mock-реализация [LlmPort] для тестирования domain services.
 */
class MockLlmPort : LlmPort {

    private val _chatCalls = mutableListOf<Pair<Prompt, TaskExecutionConfig>>()
    val chatCalls: List<Pair<Prompt, TaskExecutionConfig>> get() = _chatCalls.toList()

    private val _chatWithMessagesCalls = mutableListOf<Pair<List<ChatMessage>, TaskExecutionConfig>>()
    val chatWithMessagesCalls: List<Pair<List<ChatMessage>, TaskExecutionConfig>> get() = _chatWithMessagesCalls.toList()

    private var chatHandler: ((Prompt, TaskExecutionConfig) -> TaskResult)? = null
    private var chatWithMessagesHandler: ((List<ChatMessage>, TaskExecutionConfig) -> TaskResult)? = null

    fun respondWith(handler: (Prompt, TaskExecutionConfig) -> TaskResult) {
        chatHandler = handler
    }

    fun respondWithSuccess(content: String = "mock response") {
        chatHandler = { _, _ -> TaskResult.Success(content) }
    }

    fun respondWithError(message: String = "mock error") {
        chatHandler = { _, _ -> TaskResult.Error(message) }
    }

    fun respondWithMessages(handler: (List<ChatMessage>, TaskExecutionConfig) -> TaskResult) {
        chatWithMessagesHandler = handler
    }

    fun respondWithMessagesSuccess(content: String = "mock response") {
        chatWithMessagesHandler = { _, _ -> TaskResult.Success(content) }
    }

    fun respondWithMessagesError(message: String = "mock error") {
        chatWithMessagesHandler = { _, _ -> TaskResult.Error(message) }
    }

    override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        _chatCalls.add(prompt to config)
        return chatHandler?.invoke(prompt, config) ?: TaskResult.Success("default mock response")
    }

    override suspend fun chatWithMessages(
        messages: List<ChatMessage>,
        config: TaskExecutionConfig
    ): TaskResult {
        _chatWithMessagesCalls.add(messages to config)
        return chatWithMessagesHandler?.invoke(messages, config)
            ?: TaskResult.Success("default mock response")
    }

    var availableModels: List<ModelId> = listOf(ModelId("mock-model"))

    override suspend fun listModels(): List<ModelId> = availableModels

    fun reset() {
        _chatCalls.clear()
        _chatWithMessagesCalls.clear()
        chatHandler = null
        chatWithMessagesHandler = null
        availableModels = listOf(ModelId("mock-model"))
    }
}
