package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SimpleAgentTest {

    @Test
    fun `process with no system prompt calls chat`() = runTest {
        // Given
        val fakeLlmPort = FakeLlmPort(TaskResult.Success("response"))
        val agent = SimpleAgent(fakeLlmPort)
        val prompt = Prompt("test")
        val config = TaskExecutionConfig()

        // When
        val result = agent.process(prompt, config)

        // Then
        assertIs<TaskResult.Success>(result)
        assertEquals("response", result.content)
        assertEquals(1, fakeLlmPort.chatCalls.size)
        assertEquals(prompt, fakeLlmPort.chatCalls.first().first)
    }

    @Test
    fun `process with system prompt calls chatWithMessages`() = runTest {
        // Given
        val fakeLlmPort = FakeLlmPort(TaskResult.Success("response"))
        val agent = SimpleAgent(fakeLlmPort, systemPrompt = "You are helpful")
        val prompt = Prompt("test")
        val config = TaskExecutionConfig()

        // When
        val result = agent.process(prompt, config)

        // Then
        assertIs<TaskResult.Success>(result)
        assertEquals("response", result.content)
        assertEquals(1, fakeLlmPort.chatWithMessagesCalls.size)
        val messages = fakeLlmPort.chatWithMessagesCalls.first().first
        assertEquals(2, messages.size)
        assertEquals(ChatRole.SYSTEM, messages[0].role)
        assertEquals("You are helpful", messages[0].content)
        assertEquals(ChatRole.USER, messages[1].role)
        assertEquals("test", messages[1].content)
    }

    @Test
    fun `process propagates exception from LlmPort`() = runTest {
        // Given
        val fakeLlmPort = ThrowingLlmPort(RuntimeException("API error"))
        val agent = SimpleAgent(fakeLlmPort)
        val prompt = Prompt("test")
        val config = TaskExecutionConfig()

        // When & Then
        // SimpleAgent не обрабатывает исключения — это делает LlmAdapter
        // Поэтому исключение должно проброситься наружу
        val exception = assertFailsWith<RuntimeException> {
            agent.process(prompt, config)
        }
        assertEquals("API error", exception.message)
    }
}

/**
 * Fake implementation of LlmPort for testing.
 */
private class FakeLlmPort(
    private val result: TaskResult
) : LlmPort {
    val chatCalls = mutableListOf<Pair<Prompt, TaskExecutionConfig>>()
    val chatWithMessagesCalls = mutableListOf<Pair<List<ChatMessage>, TaskExecutionConfig>>()

    override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        chatCalls.add(prompt to config)
        return result
    }

    override suspend fun chatWithMessages(messages: List<ChatMessage>, config: TaskExecutionConfig): TaskResult {
        chatWithMessagesCalls.add(messages to config)
        return result
    }

    override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week1.domain.ModelId> = emptyList()
}

/**
 * Fake implementation of LlmPort that throws an exception on every call.
 */
private class ThrowingLlmPort(
    private val exception: RuntimeException
) : LlmPort {
    override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        throw exception
    }

    override suspend fun chatWithMessages(messages: List<ChatMessage>, config: TaskExecutionConfig): TaskResult {
        throw exception
    }

    override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week1.domain.ModelId> = emptyList()
}

