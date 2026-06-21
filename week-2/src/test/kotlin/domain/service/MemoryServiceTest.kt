package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты для [MemoryService] — сервиса управления памятью диалога.
 */
@DisplayName("MemoryService")
class MemoryServiceTest {

    private lateinit var repository: InMemoryDialogSessionRepository
    private lateinit var memoryService: MemoryService

    @BeforeEach
    fun setUp() {
        repository = InMemoryDialogSessionRepository()
        memoryService = MemoryService(repository)
    }

    @Nested
    @DisplayName("getSessionForLevel")
    inner class GetSessionForLevel {

        @Test
        @DisplayName("should create new session for TASK_LIST level")
        fun `should create new session for TASK_LIST level`() = runBlocking {
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)

            assertNotNull(session)
            assertEquals(SessionLevel.TASK_LIST, session.level)
            assertEquals(null, session.taskId)
            assertTrue(session.messages.isEmpty())
        }

        @Test
        @DisplayName("should create new session for TASK_DETAIL level")
        fun `should create new session for TASK_DETAIL level`() = runBlocking {
            val taskId = TaskId("task-1")
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId)

            assertNotNull(session)
            assertEquals(SessionLevel.TASK_DETAIL, session.level)
            assertEquals(taskId, session.taskId)
        }

        @Test
        @DisplayName("should return existing session if already created")
        fun `should return existing session if already created`() = runBlocking {
            val session1 = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
            val session2 = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)

            assertEquals(session1.id, session2.id)
        }

        @Test
        @DisplayName("should return different sessions for different tasks")
        fun `should return different sessions for different tasks`() = runBlocking {
            val session1 = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, TaskId("task-1"))
            val session2 = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, TaskId("task-2"))

            assertTrue(session1.id != session2.id)
        }
    }

    @Nested
    @DisplayName("addMessageToSession")
    inner class AddMessageToSession {

        @Test
        @DisplayName("should add message to session")
        fun `should add message to session`() = runBlocking {
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")

            val updatedSession = memoryService.addMessageToSession(
                SessionLevel.TASK_LIST,
                null,
                message
            )

            assertEquals(1, updatedSession.messages.size)
            assertEquals(message, updatedSession.messages[0])
        }

        @Test
        @DisplayName("should add multiple messages")
        fun `should add multiple messages`() = runBlocking {
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
            val updatedSession = memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

            assertEquals(2, updatedSession.messages.size)
        }
    }

    @Nested
    @DisplayName("clearSession")
    inner class ClearSession {

        @Test
        @DisplayName("should clear all messages from session")
        fun `should clear all messages from session`() = runBlocking {
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

            val clearedSession = memoryService.clearSession(SessionLevel.TASK_LIST)

            assertTrue(clearedSession.messages.isEmpty())
        }
    }

    @Nested
    @DisplayName("getRecentMessages")
    inner class GetRecentMessages {

        @Test
        @DisplayName("should return recent messages with default limit")
        fun `should return recent messages with default limit`() = runBlocking {
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val msg3 = Message.create(session.id, MessageRole.USER, "Third")

            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)
            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg3)

            val recent = memoryService.getRecentMessages(SessionLevel.TASK_LIST)

            assertEquals(3, recent.size)
        }

        @Test
        @DisplayName("should return limited number of recent messages")
        fun `should return limited number of recent messages`() = runBlocking {
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val msg3 = Message.create(session.id, MessageRole.USER, "Third")

            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)
            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg3)

            val recent = memoryService.getRecentMessages(SessionLevel.TASK_LIST, limit = 2)

            assertEquals(2, recent.size)
            assertEquals(msg2, recent[0])
            assertEquals(msg3, recent[1])
        }
    }

    @Nested
    @DisplayName("getMemoryStatus")
    inner class GetMemoryStatus {

        @Test
        @DisplayName("should return memory status with correct message count")
        fun `should return memory status with correct message count`() = runBlocking {
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

            val status = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)

            assertEquals(session.id, status.sessionId)
            assertEquals(SessionLevel.TASK_LIST, status.level)
            assertEquals(2, status.messageCount)
        }

        @Test
        @DisplayName("should return status for TASK_DETAIL level")
        fun `should return status for TASK_DETAIL level`() = runBlocking {
            val taskId = TaskId("task-1")
            val session = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId)
            val message = Message.create(session.id, MessageRole.USER, "Hello")

            memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId, message)

            val status = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId)

            assertEquals(taskId, status.taskId)
            assertEquals(1, status.messageCount)
        }
    }

    @Nested
    @DisplayName("switchToTaskLevel")
    inner class SwitchToTaskLevel {

        @Test
        @DisplayName("should switch to TASK_DETAIL level")
        fun `should switch to TASK_DETAIL level`() = runBlocking {
            val taskId = TaskId("task-1")
            val session = memoryService.switchToTaskLevel(taskId)

            assertEquals(SessionLevel.TASK_DETAIL, session.level)
            assertEquals(taskId, session.taskId)
        }
    }

    @Nested
    @DisplayName("switchToTaskListLevel")
    inner class SwitchToTaskListLevel {

        @Test
        @DisplayName("should switch to TASK_LIST level")
        fun `should switch to TASK_LIST level`() = runBlocking {
            val session = memoryService.switchToTaskListLevel()

            assertEquals(SessionLevel.TASK_LIST, session.level)
            assertEquals(null, session.taskId)
        }
    }

    /**
     * In-memory реализация DialogSessionRepository для тестирования.
     */
    private class InMemoryDialogSessionRepository : DialogSessionRepository {
        private val sessions = mutableMapOf<String, DialogSession>()

        override fun save(session: DialogSession): DialogSession {
            sessions[session.id.value] = session
            return session
        }

        override fun findById(id: SessionId): DialogSession? {
            return sessions[id.value]
        }

        override fun findByTaskId(taskId: TaskId): DialogSession? {
            return sessions.values.find { it.taskId == taskId }
        }

        override fun findActiveSession(): DialogSession? {
            return sessions.values.maxByOrNull { it.updatedAt }
        }

        override fun delete(id: SessionId) {
            sessions.remove(id.value)
        }
    }
}
