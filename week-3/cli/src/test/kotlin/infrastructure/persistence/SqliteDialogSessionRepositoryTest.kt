package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Интеграционные тесты для [SqliteDialogSessionRepository].
 *
 * Используют временный файл базы данных для каждого теста,
 * который удаляется после завершения теста.
 */
@DisplayName("SqliteDialogSessionRepository")
class SqliteDialogSessionRepositoryTest {

    private lateinit var tempDbFile: File
    private lateinit var database: SqliteDatabase
    private lateinit var repository: SqliteDialogSessionRepository

    @BeforeEach
    fun setUp() {
        tempDbFile = Files.createTempFile("test-dialog-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteDialogSessionRepository(database)
    }

    @AfterEach
    fun tearDown() {
        database.close()
        tempDbFile.delete()

        // Удаляем WAL и SHM файлы, если они существуют
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    @Nested
    @DisplayName("Сохранение и поиск сессий")
    inner class SaveAndFind {

        @Test
        @DisplayName("should save and find session by id")
        fun `should save and find session by id`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)

            repository.save(session)
            val found = repository.findById(session.id)

            assertNotNull(found)
            assertEquals(session.id, found.id)
            assertEquals(session.level, found.level)
            assertEquals(session.taskId, found.taskId)
            assertEquals(session.createdAt, found.createdAt)
        }

        @Test
        @DisplayName("should return null when session not found")
        fun `should return null when session not found`() {
            val nonExistentId = SessionId.generate()
            val found = repository.findById(nonExistentId)

            assertNull(found)
        }

        @Test
        @DisplayName("should save session with messages")
        fun `should save session with messages`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "Hello")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Hi there")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

            repository.save(sessionWithMessages)
            val found = repository.findById(session.id)

            assertNotNull(found)
            assertEquals(2, found.messages.size)
            assertEquals(msg1.id, found.messages[0].id)
            assertEquals(msg2.id, found.messages[1].id)
        }

        @Test
        @DisplayName("should update session on save")
        fun `should update session on save`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            val message = Message.create(session.id, MessageRole.USER, "Hello")
            val updatedSession = session.addMessage(message)
            repository.save(updatedSession)

            val found = repository.findById(session.id)
            assertNotNull(found)
            assertEquals(1, found.messages.size)
        }
    }

    @Nested
    @DisplayName("Поиск по taskId")
    inner class FindByTaskId {

        @Test
        @DisplayName("should find session by taskId")
        fun `should find session by taskId`() {
            val taskId = TaskId("task-1")
            val session = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)

            repository.save(session)
            val found = repository.findByTaskId(taskId)

            assertNotNull(found)
            assertEquals(session.id, found.id)
            assertEquals(taskId, found.taskId)
        }

        @Test
        @DisplayName("should return null when no session with taskId")
        fun `should return null when no session with taskId`() {
            val nonExistentTaskId = TaskId("non-existent")
            val found = repository.findByTaskId(nonExistentTaskId)

            assertNull(found)
        }

        @Test
        @DisplayName("should find most recent session when multiple exist for same taskId")
        fun `should find most recent session when multiple exist for same taskId`() {
            val taskId = TaskId("task-1")
            val session1 = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)
            repository.save(session1)

            // Небольшая задержка для разного updatedAt
            Thread.sleep(10)

            val session2 = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)
            repository.save(session2)

            val found = repository.findByTaskId(taskId)
            assertNotNull(found)
            assertEquals(session2.id, found.id)
        }
    }

    @Nested
    @DisplayName("Поиск активной сессии")
    inner class FindActiveSession {

        @Test
        @DisplayName("should find active session")
        fun `should find active session`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")
            val activeSession = session.addMessage(message)

            repository.save(activeSession)
            val found = repository.findActiveSession()

            assertNotNull(found)
            assertEquals(session.id, found.id)
        }

        @Test
        @DisplayName("should return null when no active sessions")
        fun `should return null when no active sessions`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            val found = repository.findActiveSession()
            assertNull(found)
        }

        @Test
        @DisplayName("should find most recently updated active session")
        fun `should find most recently updated active session`() {
            val session1 = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session1.id, MessageRole.USER, "First")
            repository.save(session1.addMessage(msg1))

            Thread.sleep(10)

            val session2 = DialogSession.create(SessionLevel.TASK_LIST)
            val msg2 = Message.create(session2.id, MessageRole.USER, "Second")
            repository.save(session2.addMessage(msg2))

            val found = repository.findActiveSession()
            assertNotNull(found)
            assertEquals(session2.id, found.id)
        }
    }

    @Nested
    @DisplayName("Удаление сессий")
    inner class Delete {

        @Test
        @DisplayName("should delete session by id")
        fun `should delete session by id`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            repository.delete(session.id)
            val found = repository.findById(session.id)

            assertNull(found)
        }

        @Test
        @DisplayName("should delete session with messages")
        fun `should delete session with messages`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "Hello")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Hi")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

            repository.save(sessionWithMessages)
            repository.delete(session.id)

            val found = repository.findById(session.id)
            assertNull(found)
        }

        @Test
        @DisplayName("should not throw when deleting non-existent session")
        fun `should not throw when deleting non-existent session`() {
            val nonExistentId = SessionId.generate()
            repository.delete(nonExistentId)
            // Не должно выбрасывать исключение
        }
    }

    @Nested
    @DisplayName("Транзакционность")
    inner class Transactions {

        @Test
        @DisplayName("should save session and messages atomically")
        fun `should save session and messages atomically`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val messages = (1..10).map { i ->
                Message.create(session.id, MessageRole.USER, "Message $i")
            }
            var sessionWithMessages = session
            for (msg in messages) {
                sessionWithMessages = sessionWithMessages.addMessage(msg)
            }

            repository.save(sessionWithMessages)
            val found = repository.findById(session.id)

            assertNotNull(found)
            assertEquals(10, found.messages.size)
        }

        @Test
        @DisplayName("should handle multiple saves correctly")
        fun `should handle multiple saves correctly`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val session1 = session.addMessage(msg1)
            repository.save(session1)

            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val session2 = session1.addMessage(msg2)
            repository.save(session2)

            val found = repository.findById(session.id)
            assertNotNull(found)
            assertEquals(2, found.messages.size)
        }
    }

    @Nested
    @DisplayName("Разные уровни сессий")
    inner class SessionLevels {

        @Test
        @DisplayName("should save and retrieve TASK_LIST session")
        fun `should save and retrieve TASK_LIST session`() {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            val found = repository.findById(session.id)
            assertNotNull(found)
            assertEquals(SessionLevel.TASK_LIST, found.level)
            assertNull(found.taskId)
        }

        @Test
        @DisplayName("should save and retrieve TASK_DETAIL session")
        fun `should save and retrieve TASK_DETAIL session`() {
            val taskId = TaskId("task-1")
            val session = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)
            repository.save(session)

            val found = repository.findById(session.id)
            assertNotNull(found)
            assertEquals(SessionLevel.TASK_DETAIL, found.level)
            assertEquals(taskId, found.taskId)
        }
    }
}
