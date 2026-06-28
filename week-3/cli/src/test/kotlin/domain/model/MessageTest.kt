package io.averkhogliad.ai.challenge.week3.cli.domain.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для доменной модели [Message].
 */
@DisplayName("Message")
class MessageTest {

    @Test
    @DisplayName("should create message with valid data")
    fun `should create with valid data`() {
        val sessionId = SessionId("test-session")
        val message = Message(
            id = "msg-1",
            sessionId = sessionId,
            role = MessageRole.USER,
            content = "Hello",
            timestamp = java.time.Instant.now()
        )

        assertEquals("msg-1", message.id)
        assertEquals(sessionId, message.sessionId)
        assertEquals(MessageRole.USER, message.role)
        assertEquals("Hello", message.content)
    }

    @Test
    @DisplayName("should throw exception when id is blank")
    fun `should throw when id is blank`() {
        val sessionId = SessionId("test-session")
        assertThrows<IllegalArgumentException> {
            Message(
                id = "",
                sessionId = sessionId,
                role = MessageRole.USER,
                content = "Hello",
                timestamp = java.time.Instant.now()
            )
        }
    }

    @Test
    @DisplayName("should throw exception when content is blank")
    fun `should throw when content is blank`() {
        val sessionId = SessionId("test-session")
        assertThrows<IllegalArgumentException> {
            Message(
                id = "msg-1",
                sessionId = sessionId,
                role = MessageRole.USER,
                content = "",
                timestamp = java.time.Instant.now()
            )
        }
    }

    @Test
    @DisplayName("should create message with auto-generated id")
    fun `should create with auto-generated id`() {
        val sessionId = SessionId("test-session")
        val message = Message.create(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = "Hi there"
        )

        assertTrue(message.id.isNotBlank())
        assertEquals(sessionId, message.sessionId)
        assertEquals(MessageRole.ASSISTANT, message.role)
        assertEquals("Hi there", message.content)
    }

    @Test
    @DisplayName("should create messages with different roles")
    fun `should create messages with different roles`() {
        val sessionId = SessionId("test-session")

        val systemMsg = Message.create(sessionId, MessageRole.SYSTEM, "System message")
        val userMsg = Message.create(sessionId, MessageRole.USER, "User message")
        val assistantMsg = Message.create(sessionId, MessageRole.ASSISTANT, "Assistant message")

        assertEquals(MessageRole.SYSTEM, systemMsg.role)
        assertEquals(MessageRole.USER, userMsg.role)
        assertEquals(MessageRole.ASSISTANT, assistantMsg.role)
    }

    @Test
    @DisplayName("should generate unique ids for different messages")
    fun `should generate unique ids`() {
        val sessionId = SessionId("test-session")
        val msg1 = Message.create(sessionId, MessageRole.USER, "First")
        val msg2 = Message.create(sessionId, MessageRole.USER, "Second")

        assertTrue(msg1.id != msg2.id)
    }
}
