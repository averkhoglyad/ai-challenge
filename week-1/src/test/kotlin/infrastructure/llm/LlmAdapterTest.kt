package io.averkhogliad.ai.challenge.week1.infrastructure.llm

import io.averkhogliad.ai.challenge.utils.llm.*
import io.averkhogliad.ai.challenge.week1.domain.ModelId
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage as DomainChatMessage

class LlmAdapterTest {

    private val defaultModelId = ModelId("default-model")

    // ─────────────────────────────────────────────
    // chat() — успешный ответ
    // ─────────────────────────────────────────────

    @Test
    fun `chat should return Success for normal response`() = runBlocking {
        val mockClient = MockLlmClient(
            chatResponse = ChatResponse(
                content = "Hello, world!",
                finishReason = "stop",
                usage = ChatResponse.Usage(
                    promptTokens = 10,
                    completionTokens = 5,
                    totalTokens = 15
                )
            )
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        val result = adapter.chat(Prompt("Hi"), TaskExecutionConfig())

        assertIs<TaskResult.Success>(result)
        assertEquals("Hello, world!", result.content)
        assertEquals("stop", result.metadata["finishReason"])
        assertEquals(10, result.metadata["promptTokens"])
        assertEquals(5, result.metadata["completionTokens"])
        assertEquals(15, result.metadata["totalTokens"])
    }

    // ─────────────────────────────────────────────
    // chat() — обработка ошибок
    // ─────────────────────────────────────────────

    @Test
    fun `chat should return Error when LlmException is thrown`() = runBlocking {
        val mockClient = MockLlmClient(
            onChat = { _, _, _, _, _ -> throw LlmException("API error") }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        val result = adapter.chat(Prompt("Hi"), TaskExecutionConfig())

        assertIs<TaskResult.Error>(result)
        assertEquals("API error", result.message)
        assertNotNull(result.cause)
        assertTrue(result.cause is LlmException)
    }

    @Test
    fun `chat should return Error for unexpected exceptions`() = runBlocking {
        val mockClient = MockLlmClient(
            onChat = { _, _, _, _, _ -> throw RuntimeException("Unexpected") }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        val result = adapter.chat(Prompt("Hi"), TaskExecutionConfig())

        assertIs<TaskResult.Error>(result)
        assertTrue(result.message.contains("Unexpected error"))
        assertNotNull(result.cause)
    }

    // ─────────────────────────────────────────────
    // chat() — передача параметров (температура)
    // ─────────────────────────────────────────────

    @Test
    fun `chat should pass temperature from config to LLM client`() = runBlocking {
        var capturedParams: ChatParameters? = null
        val mockClient = MockLlmClient(
            onChat = { _, _, params, _, _ ->
                capturedParams = params
                ChatResponse("ok", "stop", null)
            }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        adapter.chat(Prompt("Hi"), TaskExecutionConfig(temperature = 0.3))

        assertNotNull(capturedParams)
        assertEquals(0.3, capturedParams!!.temperature)
    }

    @Test
    fun `chat should pass maxTokens from config to LLM client`() = runBlocking {
        var capturedParams: ChatParameters? = null
        val mockClient = MockLlmClient(
            onChat = { _, _, params, _, _ ->
                capturedParams = params
                ChatResponse("ok", "stop", null)
            }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        adapter.chat(Prompt("Hi"), TaskExecutionConfig(maxTokens = 800))

        assertNotNull(capturedParams)
        assertEquals(800, capturedParams!!.maxTokens)
    }

    @Test
    fun `chat should pass stopSequences from config to LLM client`() = runBlocking {
        var capturedParams: ChatParameters? = null
        val mockClient = MockLlmClient(
            onChat = { _, _, params, _, _ ->
                capturedParams = params
                ChatResponse("ok", "stop", null)
            }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        adapter.chat(Prompt("Hi"), TaskExecutionConfig(stopSequences = listOf("END", "STOP")))

        assertNotNull(capturedParams)
        assertEquals(listOf("END", "STOP"), capturedParams!!.stop)
    }

    @Test
    fun `chat should pass null stop when stopSequences is empty`() = runBlocking {
        var capturedParams: ChatParameters? = null
        val mockClient = MockLlmClient(
            onChat = { _, _, params, _, _ ->
                capturedParams = params
                ChatResponse("ok", "stop", null)
            }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        adapter.chat(Prompt("Hi"), TaskExecutionConfig(stopSequences = emptyList()))

        assertNotNull(capturedParams)
        assertEquals(null, capturedParams!!.stop)
    }

    // ─────────────────────────────────────────────
    // chat() — modelId
    // ─────────────────────────────────────────────

    @Test
    fun `chat should use config modelId when specified`() = runBlocking {
        var capturedModel: String? = null
        val mockClient = MockLlmClient(
            onChat = { _, _, _, model, _ ->
                capturedModel = model
                ChatResponse("ok", "stop", null)
            }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        adapter.chat(Prompt("Hi"), TaskExecutionConfig(modelId = ModelId("gpt-4")))

        assertEquals("gpt-4", capturedModel)
    }

    @Test
    fun `chat should fallback to defaultModelId when config modelId is null`() = runBlocking {
        var capturedModel: String? = null
        val mockClient = MockLlmClient(
            onChat = { _, _, _, model, _ ->
                capturedModel = model
                ChatResponse("ok", "stop", null)
            }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        adapter.chat(Prompt("Hi"), TaskExecutionConfig(modelId = null))

        assertEquals("default-model", capturedModel)
    }

    // ─────────────────────────────────────────────
    // chat() — content_filter
    // ─────────────────────────────────────────────

    @Test
    fun `chat should return Error when response is filtered`() = runBlocking {
        val mockClient = MockLlmClient(
            chatResponse = ChatResponse(
                content = "",
                finishReason = "content_filter",
                usage = null
            )
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        val result = adapter.chat(Prompt("Hi"), TaskExecutionConfig())

        assertIs<TaskResult.Error>(result)
        assertEquals("Response was blocked by content filter", result.message)
    }

    // ─────────────────────────────────────────────
    // chat() — length (truncated → Partial)
    // ─────────────────────────────────────────────

    @Test
    fun `chat should return Partial when response is truncated`() = runBlocking {
        val mockClient = MockLlmClient(
            chatResponse = ChatResponse(
                content = "Partial output...",
                finishReason = "length",
                usage = null
            )
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        val result = adapter.chat(Prompt("Hi"), TaskExecutionConfig())

        assertIs<TaskResult.Partial>(result)
        assertEquals("Partial output...", result.content)
        assertEquals(1.0, result.progress)
    }

    // ─────────────────────────────────────────────
    // chatWithMessages() — успешный ответ
    // ─────────────────────────────────────────────

    @Test
    fun `chatWithMessages should map domain messages to infra messages`() = runBlocking {
        var capturedMessages: List<ChatMessage>? = null
        val mockClient = MockLlmClient(
            onChatWithMessages = { messages, _, _, _ ->
                capturedMessages = messages
                ChatResponse("response", "stop", null)
            }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        val domainMessages = listOf(
            DomainChatMessage.system("You are helpful"),
            DomainChatMessage.user("Hello"),
            DomainChatMessage.assistant("Hi there!")
        )
        val config = TaskExecutionConfig(modelId = ModelId("custom-model"))

        val result = adapter.chatWithMessages(domainMessages, config)

        assertIs<TaskResult.Success>(result)
        assertNotNull(capturedMessages)
        assertEquals(3, capturedMessages!!.size)
        assertEquals("system", capturedMessages!![0].role)
        assertEquals("You are helpful", capturedMessages!![0].content)
        assertEquals("user", capturedMessages!![1].role)
        assertEquals("Hello", capturedMessages!![1].content)
        assertEquals("assistant", capturedMessages!![2].role)
        assertEquals("Hi there!", capturedMessages!![2].content)
    }

    @Test
    fun `chatWithMessages should pass parameters and model correctly`() = runBlocking {
        var capturedParams: ChatParameters? = null
        var capturedModel: String? = null
        val mockClient = MockLlmClient(
            onChatWithMessages = { _, params, model, _ ->
                capturedParams = params
                capturedModel = model
                ChatResponse("ok", "stop", null)
            }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        val domainMessages = listOf(DomainChatMessage.user("Hi"))
        val config = TaskExecutionConfig(
            temperature = 0.9,
            maxTokens = 1000,
            stopSequences = listOf("."),
            modelId = ModelId("gpt-4")
        )

        adapter.chatWithMessages(domainMessages, config)

        assertNotNull(capturedParams)
        assertEquals(0.9, capturedParams!!.temperature)
        assertEquals(1000, capturedParams!!.maxTokens)
        assertEquals(listOf("."), capturedParams!!.stop)
        assertEquals("gpt-4", capturedModel)
    }

    @Test
    fun `chatWithMessages should return Error when LlmException is thrown`() = runBlocking {
        val mockClient = MockLlmClient(
            onChatWithMessages = { _, _, _, _ -> throw LlmException("API failure") }
        )
        val adapter = LlmAdapter(mockClient, defaultModelId)

        val result = adapter.chatWithMessages(
            listOf(DomainChatMessage.user("Hi")),
            TaskExecutionConfig()
        )

        assertIs<TaskResult.Error>(result)
        assertEquals("API failure", result.message)
    }
}
