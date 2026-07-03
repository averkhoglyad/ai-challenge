package io.averkhogliad.ai.challenge.week4.cli.it

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Message
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MessageRole
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDialogSessionRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files

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
class MemoryIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteDialogSessionRepository
    lateinit var memoryService: MemoryService

    beforeTest {
        tempDbFile = Files.createTempFile("test-memory-integration-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteDialogSessionRepository(database)
        memoryService = MemoryService(repository)
    }

    afterTest {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "should handle complete memory lifecycle" {
        runTest {
            // 1. Создание сессии уровня TASK_LIST
            val taskListSession = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
            taskListSession shouldNotBe null
            taskListSession.level shouldBe SessionLevel.TASK_LIST
            taskListSession.messages.shouldHaveSize(0)

            // 2. Добавление сообщений в TASK_LIST сессию
            val msg1 = Message.create(taskListSession.id, MessageRole.USER, "Покажи список задач")
            val msg2 = Message.create(taskListSession.id, MessageRole.ASSISTANT, "Вот ваши задачи: ...")

            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
            memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

            // 3. Проверка сохранения в БД
            val savedSession = repository.findById(taskListSession.id)
            savedSession shouldNotBe null
            savedSession!!.messages shouldHaveSize 2

            // 4. Переключение на уровень TASK_DETAIL
            val taskId = TaskId("task-123")
            val taskDetailSession = memoryService.switchToTaskLevel(taskId)
            taskDetailSession shouldNotBe null
            taskDetailSession.level shouldBe SessionLevel.TASK_DETAIL
            taskDetailSession.taskId shouldBe taskId

            // 5. Добавление сообщений в TASK_DETAIL сессию
            val msg3 = Message.create(taskDetailSession.id, MessageRole.USER, "Расскажи подробнее о задаче")
            val msg4 = Message.create(taskDetailSession.id, MessageRole.ASSISTANT, "Эта задача включает...")

            memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId, msg3)
            memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId, msg4)

            // 6. Проверка, что обе сессии сохранены
            val taskListStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
            val taskDetailStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId)

            taskListStatus.messageCount shouldBe 2
            taskDetailStatus.messageCount shouldBe 2

            // 7. Переключение обратно на TASK_LIST
            val backToTaskList = memoryService.switchToTaskListLevel()
            backToTaskList.id shouldBe taskListSession.id
            backToTaskList.messages shouldHaveSize 2

            // 8. Очистка TASK_LIST сессии
            val clearedSession = memoryService.clearSession(SessionLevel.TASK_LIST)
            clearedSession.messages.shouldHaveSize(0)

            // 9. Проверка, что TASK_DETAIL сессия не затронута
            val taskDetailAfterClear = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId)
            taskDetailAfterClear.messages shouldHaveSize 2

            // 10. Получение последних сообщений
            val recentMessages = memoryService.getRecentMessages(SessionLevel.TASK_DETAIL, taskId, limit = 1)
            recentMessages shouldHaveSize 1
            recentMessages[0].id shouldBe msg4.id
        }
    }

    "should maintain separate sessions for different tasks" {
        runTest {
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

            status1.messageCount shouldBe 1
            status2.messageCount shouldBe 1
            (session1.id != session2.id) shouldBe true

            // Очистка одной сессии не влияет на другую
            memoryService.clearSession(SessionLevel.TASK_DETAIL, taskId1)

            val status1AfterClear = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId1)
            val status2AfterClear = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId2)

            status1AfterClear.messageCount shouldBe 0
            status2AfterClear.messageCount shouldBe 1
        }
    }

    "should persist messages across service restarts" {
        runTest {
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
            restoredSession.id shouldBe session.id
            restoredSession.messages shouldHaveSize 2
            restoredSession.messages[0].id shouldBe msg1.id
            restoredSession.messages[1].id shouldBe msg2.id
        }
    }
})
