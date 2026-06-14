package io.averkhogliad.ai.challenge.week1.domain.context

import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
import io.averkhogliad.ai.challenge.week1.domain.service.MockLlmPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlidingWindowCompressorTest {

    private val mockLlm = MockLlmPort()
    private val compressor = SlidingWindowCompressor(mockLlm)

    private fun makeMessages(count: Int): List<ChatMessage> =
        (1..count).map { ChatMessage(ChatRole.USER, "Message $it") }

    @Test
    fun `should not compress when messages size equals windowSize`() = runTest {
        val config = ContextCompressionConfig(windowSize = 5, blockSize = 3)
        val messages = makeMessages(5)

        val result = compressor.compress(messages, config, null)

        assertEquals(5, result.recentMessages.size)
        assertNull(result.summary)
        assertEquals(0, result.compressedMessageCount)
        assertTrue(mockLlm.chatCalls.isEmpty())
    }

    @Test
    fun `should not compress when messages size less than windowSize`() = runTest {
        val config = ContextCompressionConfig(windowSize = 10, blockSize = 5)
        val messages = makeMessages(3)

        val result = compressor.compress(messages, config, null)

        assertEquals(3, result.recentMessages.size)
        assertNull(result.summary)
        assertEquals(0, result.compressedMessageCount)
    }

    @Test
    fun `should compress when messages size exceeds windowSize`() = runTest {
        val config = ContextCompressionConfig(windowSize = 5, blockSize = 3)
        val messages = makeMessages(8)
        mockLlm.respondWithSuccess("Summary of messages")

        val result = compressor.compress(messages, config, null)

        assertEquals(5, result.recentMessages.size)
        assertEquals("Summary of messages", result.summary)
        assertEquals(3, result.compressedMessageCount)
        assertEquals(1, mockLlm.chatCalls.size)
    }

    @Test
    fun `should use initial summary prompt when previousSummary is null`() = runTest {
        val config = ContextCompressionConfig(windowSize = 3, blockSize = 2)
        val messages = makeMessages(5)
        mockLlm.respondWithSuccess("Initial summary")

        compressor.compress(messages, config, null)

        val prompt = mockLlm.chatCalls[0].first.value
        assertTrue(prompt.contains("summarizer"))
        assertTrue(prompt.contains("Instructions"))
    }

    @Test
    fun `should use incremental summary prompt when previousSummary is not null`() = runTest {
        val config = ContextCompressionConfig(windowSize = 3, blockSize = 2)
        val messages = makeMessages(5)
        mockLlm.respondWithSuccess("Updated summary")

        compressor.compress(messages, config, "Previous summary")

        val prompt = mockLlm.chatCalls[0].first.value
        assertTrue(prompt.contains("Existing Summary"))
        assertTrue(prompt.contains("Previous summary"))
        assertTrue(prompt.contains("New Messages to Integrate"))
    }

    @Test
    fun `should handle empty messages list`() = runTest {
        val config = ContextCompressionConfig(windowSize = 5, blockSize = 3)

        val result = compressor.compress(emptyList(), config, null)

        assertEquals(0, result.recentMessages.size)
        assertNull(result.summary)
        assertEquals(0, result.compressedMessageCount)
    }

    @Test
    fun `should preserve previousSummary when no compression needed`() = runTest {
        val config = ContextCompressionConfig(windowSize = 10, blockSize = 5)
        val messages = makeMessages(3)

        val result = compressor.compress(messages, config, "Existing summary")

        assertEquals("Existing summary", result.summary)
        assertEquals(3, result.recentMessages.size)
    }

    @Test
    fun `should take last windowSize messages as recent`() = runTest {
        val config = ContextCompressionConfig(windowSize = 3, blockSize = 2)
        val messages = makeMessages(6)
        mockLlm.respondWithSuccess("Summary")

        val result = compressor.compress(messages, config, null)

        assertEquals("Message 4", result.recentMessages[0].content)
        assertEquals("Message 5", result.recentMessages[1].content)
        assertEquals("Message 6", result.recentMessages[2].content)
    }

    @Test
    fun `should handle LLM error gracefully`() = runTest {
        val config = ContextCompressionConfig(windowSize = 3, blockSize = 2)
        val messages = makeMessages(5)
        mockLlm.respondWithError("LLM failure")

        val result = compressor.compress(messages, config, null)

        assertTrue(result.summary?.contains("Summary generation failed") == true)
    }

    @Test
    fun `should compress exactly blockSize messages when oldMessages exceed blockSize`() = runTest {
        val config = ContextCompressionConfig(windowSize = 3, blockSize = 2)
        val messages = makeMessages(7) // old = 4, block = last 2 of old
        mockLlm.respondWithSuccess("Summary")

        val result = compressor.compress(messages, config, null)

        assertEquals(2, result.compressedMessageCount)
    }

    @Test
    fun `should compress all old messages when oldMessages less than blockSize`() = runTest {
        val config = ContextCompressionConfig(windowSize = 5, blockSize = 3)
        val messages = makeMessages(7) // old = 2, block = all 2
        mockLlm.respondWithSuccess("Summary")

        val result = compressor.compress(messages, config, null)

        assertEquals(2, result.compressedMessageCount)
    }
}
