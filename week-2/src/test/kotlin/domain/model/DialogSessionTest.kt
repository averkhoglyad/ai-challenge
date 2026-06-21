package io.averkhogliad.ai.challenge.week2.domain.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тесты для доменной модели [DialogSession].
 */
@DisplayName("DialogSession")
class DialogSessionTest {

    @Nested
    @DisplayName("Создание сессии")
    inner class Creation {

        @Test
        @DisplayName("should create session with TASK_LIST level")
        fun `should create with TASK_LIST level`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)

            assertEquals(SessionLevel.TASK_LIST, session.level)
            assertEquals(null, session.taskId)
            assertTrue(session.messages.isEmpty())
            assertTrue(session.id.value.isNotBlank())
        }

        @Test
        @DisplayName("should create session with TASK_DETAIL level and taskId")
        fun `should create with TASK_DETAIL level and taskId`() {
            val taskId = TaskId("task-1")
            val session = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)

            assertEquals(SessionLevel.TASK_DETAIL, session.level)
            assertEquals(taskId, session.taskId)
            assertTrue(session.messages.isEmpty())
        }

        @Test
        @DisplayName("should throw exception when TASK_DETAIL without taskId")
        fun `should throw when TASK_DETAIL without taskId`() {
            assertThrows<IllegalArgumentException> {
                DialogSession(
                    id = SessionId.generate(),
                    level = SessionLevel.TASK_DETAIL,
                    taskId = null,
                    messages = emptyList(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            }
        }
    }

    @Nested
    @DisplayName("Активность сессии")
    inner class Activity {

        @Test
        @DisplayName("should be inactive when no messages")
        fun `should be inactive when no messages`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            assertFalse(session.isActive())
        }

        @Test
        @DisplayName("should be active when has messages")
        fun `should be active when has messages`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")
            val sessionWithMessage = session.addMessage(message)

            assertTrue(sessionWithMessage.isActive())
        }
    }

    @Nested
    @DisplayName("Добавление сообщений")
    inner class AddMessage {

        @Test
        @DisplayName("should add message to session")
        fun `should add message to session`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")

            val updatedSession = session.addMessage(message)

            assertEquals(1, updatedSession.messages.size)
            assertEquals(message, updatedSession.messages[0])
        }

        @Test
        @DisplayName("should throw exception when message sessionId doesn't match")
        fun `should throw when message sessionId doesn't match`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val otherSessionId = SessionId.generate()
            val message = Message.create(otherSessionId, MessageRole.USER, "Hello")

            assertThrows<IllegalArgumentException> {
                session.addMessage(message)
            }
        }

        @Test
        @DisplayName("should update updatedAt when adding message")
        fun `should update updatedAt when adding message`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val originalUpdatedAt = session.updatedAt
            val message = Message.create(session.id, MessageRole.USER, "Hello")

            val updatedSession = session.addMessage(message)

            assertTrue(updatedSession.updatedAt >= originalUpdatedAt)
        }

        @Test
        @DisplayName("should add multiple messages")
        fun `should add multiple messages`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

            val sessionWithMsg1 = session.addMessage(msg1)
            val sessionWithMsg2 = sessionWithMsg1.addMessage(msg2)

            assertEquals(2, sessionWithMsg2.messages.size)
            assertEquals(msg1, sessionWithMsg2.messages[0])
            assertEquals(msg2, sessionWithMsg2.messages[1])
        }
    }

    @Nested
    @DisplayName("Очистка сообщений")
    inner class ClearMessages {

        @Test
        @DisplayName("should clear all messages")
        fun `should clear all messages`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

            val clearedSession = sessionWithMessages.clearMessages()

            assertTrue(clearedSession.messages.isEmpty())
            assertFalse(clearedSession.isActive())
        }

        @Test
        @DisplayName("should update updatedAt when clearing")
        fun `should update updatedAt when clearing`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")
            val sessionWithMessage = session.addMessage(message)
            val originalUpdatedAt = sessionWithMessage.updatedAt

            val clearedSession = sessionWithMessage.clearMessages()

            assertTrue(clearedSession.updatedAt >= originalUpdatedAt)
        }
    }

    @Nested
    @DisplayName("Получение последних сообщений")
    inner class GetRecentMessages {

        @Test
        @DisplayName("should return last N messages")
        fun `should return last N messages`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val msg3 = Message.create(session.id, MessageRole.USER, "Third")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2).addMessage(msg3)

            val recent = sessionWithMessages.getRecentMessages(2)

            assertEquals(2, recent.size)
            assertEquals(msg2, recent[0])
            assertEquals(msg3, recent[1])
        }

        @Test
        @DisplayName("should return all messages when limit is greater than count")
        fun `should return all messages when limit is greater than count`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

            val recent = sessionWithMessages.getRecentMessages(10)

            assertEquals(2, recent.size)
        }

        @Test
        @DisplayName("should return empty list when no messages")
        fun `should return empty list when no messages`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)

            val recent = session.getRecentMessages(5)

            assertTrue(recent.isEmpty())
        }
    }
}
