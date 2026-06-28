package io.averkhogliad.ai.challenge.week3.cli.integration

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Message
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MessageRole
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDialogSessionRepository

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Интеграционный тест для проверки сквозной работы с памятью диалога.
 *
 * Проверяет полный цикл:
 * - Создание сессии
 * - Добавление сообщений
 * - Переключение между уровнями
 * - Проверка сохранения в БД
 * - Очистка сессии
 */
@DisplayName("Memory Integration")
class MemoryIntegrationTest {

    private lateinit var tempDbFile: File
    private lateinit var database: SqliteDatabase
    private lateinit var repository: SqliteDialogSessionRepository
    private lateinit var memoryService: MemoryService

    @BeforeEach
    fun setUp() {
        tempDbFile = Files.createTempFile("test-memory-integration-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteDialogSessionRepository(database)
        memoryService = MemoryService(repository)
    }

    @AfterEach
    fun tearDown() {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    @Test
    @DisplayName("should handle complete memory lifecycle")
    fun `should handle complete memory lifecycle`() = runBlocking {
        // 1. Создание сессии уровня TASK_LIST
        val taskListSession = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
        assertNotNull(taskListSession)
        assertEquals(SessionLevel.TASK_LIST, taskListSession.level)
        assertTrue(taskListSession.messages.isEmpty())

        // 2. Добавление сообщений в TASK_LIST сессию
        val msg1 = Message.create(taskListSession.id, MessageRole.USER, "Покажи список задач")
        val msg2 = Message.create(taskListSession.id, MessageRole.ASSISTANT, "Вот ваши задачи: ...")

        memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
        memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

        // 3. Проверка сохранения в БД
        val savedSession = repository.findById(taskListSession.id)
        assertNotNull(savedSession)
        assertEquals(2, savedSession.messages.size)

        // 4. Переключение на уровень TASK_DETAIL
        val taskId = TaskId("task-123")
        val taskDetailSession = memoryService.switchToTaskLevel(taskId)
        assertNotNull(taskDetailSession)
        assertEquals(SessionLevel.TASK_DETAIL, taskDetailSession.level)
        assertEquals(taskId, taskDetailSession.taskId)

        // 5. Добавление сообщений в TASK_DETAIL сессию
        val msg3 = Message.create(taskDetailSession.id, MessageRole.USER, "Расскажи подробнее о задаче")
        val msg4 = Message.create(taskDetailSession.id, MessageRole.ASSISTANT, "Эта задача включает...")

        memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId, msg3)
        memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId, msg4)

        // 6. Проверка, что обе сессии сохранены
        val taskListStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
        val taskDetailStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId)

        assertEquals(2, taskListStatus.messageCount)
        assertEquals(2, taskDetailStatus.messageCount)

        // 7. Переключение обратно на TASK_LIST
        val backToTaskList = memoryService.switchToTaskListLevel()
        assertEquals(taskListSession.id, backToTaskList.id)
        assertEquals(2, backToTaskList.messages.size)

        // 8. Очистка TASK_LIST сессии
        val clearedSession = memoryService.clearSession(SessionLevel.TASK_LIST)
        assertTrue(clearedSession.messages.isEmpty())

        // 9. Проверка, что TASK_DETAIL сессия не затронута
        val taskDetailAfterClear = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId)
        assertEquals(2, taskDetailAfterClear.messages.size)

        // 10. Получение последних сообщений
        val recentMessages = memoryService.getRecentMessages(SessionLevel.TASK_DETAIL, taskId, limit = 1)
        assertEquals(1, recentMessages.size)
        assertEquals(msg4.id, recentMessages[0].id)
    }

    @Test
    @DisplayName("should maintain separate sessions for different tasks")
    fun `should maintain separate sessions for different tasks`() = runBlocking {
        val taskId1 = TaskId("task-1")
        val taskId2 = TaskId("task-2")

        // Создание сессий для разных задач
        val session1 = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId1)
        val session2 = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId2)

        // Добавление сообщений в каждую сессию
        val msg1 = Message.create(session1.id, MessageRole.USER, "Сообщение для задачи 1")
        val msg2 = Message.create(session2.id, MessageRole.USER, "Сообщение для задачи 2")

        memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId1, msg1)
        memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId2, msg2)

        // Проверка, что сессии независимы
        val status1 = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId1)
        val status2 = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId2)

        assertEquals(1, status1.messageCount)
        assertEquals(1, status2.messageCount)
        assertTrue(session1.id != session2.id)

        // Очистка одной сессии не влияет на другую
        memoryService.clearSession(SessionLevel.TASK_DETAIL, taskId1)

        val status1AfterClear = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId1)
        val status2AfterClear = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId2)

        assertEquals(0, status1AfterClear.messageCount)
        assertEquals(1, status2AfterClear.messageCount)
    }

    @Test
    @DisplayName("should persist messages across service restarts")
    fun `should persist messages across service restarts`() = runBlocking {
        // Создание сессии и добавление сообщений
        val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
        val msg1 = Message.create(session.id, MessageRole.USER, "Первое сообщение")
        val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Второе сообщение")

        memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
        memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

        // Создание нового сервиса с тем же репозиторием (имитация перезапуска)
        val newMemoryService = MemoryService(repository)

        // Проверка, что сообщения сохранены
        val restoredSession = newMemoryService.getSessionForLevel(SessionLevel.TASK_LIST)
        assertEquals(session.id, restoredSession.id)
        assertEquals(2, restoredSession.messages.size)
        assertEquals(msg1.id, restoredSession.messages[0].id)
        assertEquals(msg2.id, restoredSession.messages[1].id)
    }
}
