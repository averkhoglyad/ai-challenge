package io.averkhogliad.ai.challenge.week1.domain.context

import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialogContextTest {

    @Test
    fun `should create valid DialogContext with summary`() {
        val messages = listOf(
            ChatMessage(ChatRole.USER, "Hello"),
            ChatMessage(ChatRole.ASSISTANT, "Hi there")
        )
        val context = DialogContext(
            summary = "Previous conversation summary",
            recentMessages = messages,
            compressedMessageCount = 5
        )

        assertEquals("Previous conversation summary", context.summary)
        assertEquals(2, context.recentMessages.size)
        assertEquals(5, context.compressedMessageCount)
    }

    @Test
    fun `should create DialogContext without summary`() {
        val messages = listOf(ChatMessage(ChatRole.USER, "Hello"))
        val context = DialogContext(
            summary = null,
            recentMessages = messages,
            compressedMessageCount = 0
        )

        assertEquals(null, context.summary)
        assertEquals(1, context.recentMessages.size)
        assertEquals(0, context.compressedMessageCount)
    }

    @Test
    fun `should throw when compressedMessageCount is negative`() {
        assertThrows<IllegalArgumentException> {
            DialogContext(
                summary = null,
                recentMessages = emptyList(),
                compressedMessageCount = -1
            )
        }
    }

    @Test
    fun `toMessagesList should include summary in system message`() {
        val messages = listOf(
            ChatMessage(ChatRole.USER, "Hello"),
            ChatMessage(ChatRole.ASSISTANT, "Hi")
        )
        val context = DialogContext(
            summary = "User greeted assistant",
            recentMessages = messages,
            compressedMessageCount = 3
        )

        val result = context.toMessagesList("You are a helpful assistant")

        assertEquals(3, result.size) // system + 2 messages
        assertEquals("system", result[0].role)
        assertTrue(result[0].content.contains("You are a helpful assistant"))
        assertTrue(result[0].content.contains("User greeted assistant"))
        assertEquals("user", result[1].role)
        assertEquals("Hello", result[1].content)
        assertEquals("assistant", result[2].role)
        assertEquals("Hi", result[2].content)
    }

    @Test
    fun `toMessagesList should not include summary section when summary is null`() {
        val messages = listOf(ChatMessage(ChatRole.USER, "Hello"))
        val context = DialogContext(
            summary = null,
            recentMessages = messages,
            compressedMessageCount = 0
        )

        val result = context.toMessagesList("System prompt")

        assertEquals(2, result.size) // system + 1 message
        assertEquals("system", result[0].role)
        assertEquals("System prompt", result[0].content)
        assertTrue(!result[0].content.contains("Context summary"))
    }

    @Test
    fun `toMessagesList should handle empty recentMessages`() {
        val context = DialogContext(
            summary = "Summary only",
            recentMessages = emptyList(),
            compressedMessageCount = 5
        )

        val result = context.toMessagesList("System")

        assertEquals(1, result.size)
        assertEquals("system", result[0].role)
        assertTrue(result[0].content.contains("Summary only"))
    }

    @Test
    fun `estimateTokenCount should return zero for empty context`() {
        val context = DialogContext(
            summary = null,
            recentMessages = emptyList(),
            compressedMessageCount = 0
        )

        assertEquals(0, context.estimateTokenCount())
    }

    @Test
    fun `estimateTokenCount should estimate tokens from summary`() {
        val context = DialogContext(
            summary = "This is a summary with some content",
            recentMessages = emptyList(),
            compressedMessageCount = 0
        )

        // 38 chars / 4 = 9 tokens (approx)
        val tokens = context.estimateTokenCount()
        assertTrue(tokens > 0)
        assertTrue(tokens < 20)
    }

    @Test
    fun `estimateTokenCount should estimate tokens from messages`() {
        val messages = listOf(
            ChatMessage(ChatRole.USER, "Hello world"),
            ChatMessage(ChatRole.ASSISTANT, "Hi there")
        )
        val context = DialogContext(
            summary = null,
            recentMessages = messages,
            compressedMessageCount = 0
        )

        // "Hello world" = 11 chars, "Hi there" = 8 chars, total = 19 chars / 4 = 4 tokens
        val tokens = context.estimateTokenCount()
        assertTrue(tokens > 0)
    }

    @Test
    fun `estimateTokenCount should combine summary and messages`() {
        val messages = listOf(ChatMessage(ChatRole.USER, "Test message"))
        val context = DialogContext(
            summary = "Summary text here",
            recentMessages = messages,
            compressedMessageCount = 0
        )

        val tokens = context.estimateTokenCount()
        assertTrue(tokens > 0)
    }
}
