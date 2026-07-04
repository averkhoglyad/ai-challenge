package io.averkhogliad.ai.challenge.week2.it

import io.averkhogliad.ai.challenge.week2.domain.model.Message
import io.averkhogliad.ai.challenge.week2.domain.model.MessageRole
import io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files

class MemoryIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteDialogSessionRepository
    lateinit var taskRepo: SqliteTaskRepository
    lateinit var taskStepRepo: SqliteTaskStepRepository
    lateinit var factRepo: SqliteFactRepository
    lateinit var memoryService: MemoryService

    beforeEach {
        tempDbFile = Files.createTempFile("test-memory-it-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteDialogSessionRepository(database)
        taskRepo = SqliteTaskRepository(database)
        taskStepRepo = SqliteTaskStepRepository(database)
        factRepo = SqliteFactRepository(database)
        memoryService = MemoryService(repository, taskRepo, taskStepRepo, factRepo)
    }

    afterEach {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "memory lifecycle" - {

        "handles complete memory lifecycle across session levels" {
            runTest {
                // 1. create task list session
                val taskListSession = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                taskListSession.shouldNotBeNull()
                taskListSession.level shouldBe SessionLevel.TASK_LIST
                taskListSession.messages.shouldHaveSize(0)

                // 2. add messages
                val msg1 = Message.create(taskListSession.id, MessageRole.USER, "Покажи список задач")
                val msg2 = Message.create(taskListSession.id, MessageRole.ASSISTANT, "Вот ваши задачи: ...")

                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

                // 3. verify in DB
                val savedSession = repository.findById(taskListSession.id)
                savedSession.shouldNotBeNull()
                savedSession.messages shouldHaveSize 2

                // 4. switch to task detail level
                val taskId = TaskId("task-123")
                val taskDetailSession = memoryService.switchToTaskLevel(taskId)
                taskDetailSession.shouldNotBeNull()
                taskDetailSession.level shouldBe SessionLevel.TASK_DETAIL
                taskDetailSession.taskId shouldBe taskId

                // 5. add detail messages
                val msg3 = Message.create(taskDetailSession.id, MessageRole.USER, "Расскажи подробнее о задаче")
                val msg4 = Message.create(taskDetailSession.id, MessageRole.ASSISTANT, "Эта задача включает...")

                memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId, msg3)
                memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId, msg4)

                // 6. verify both sessions
                val taskListStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
                val taskDetailStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId)

                taskListStatus.messageCount shouldBe 2
                taskDetailStatus.messageCount shouldBe 2

                // 7. switch back
                val backToTaskList = memoryService.switchToTaskListLevel()
                backToTaskList.id shouldBe taskListSession.id
                backToTaskList.messages shouldHaveSize 2

                // 8. clear task list session
                val clearedSession = memoryService.clearSession(SessionLevel.TASK_LIST)
                clearedSession.messages.shouldHaveSize(0)

                // 9. verify task detail untouched
                val taskDetailAfterClear = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId)
                taskDetailAfterClear.messages shouldHaveSize 2

                // 10. get recent messages
                val recentMessages = memoryService.getRecentMessages(SessionLevel.TASK_DETAIL, taskId, limit = 1)
                recentMessages shouldHaveSize 1
                recentMessages[0].id shouldBe msg4.id
            }
        }

        "maintains separate sessions for different tasks" {
            runTest {
                val taskId1 = TaskId("task-1")
                val taskId2 = TaskId("task-2")

                val session1 = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId1)
                val session2 = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId2)

                val msg1 = Message.create(session1.id, MessageRole.USER, "Сообщение для задачи 1")
                val msg2 = Message.create(session2.id, MessageRole.USER, "Сообщение для задачи 2")

                memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId1, msg1)
                memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId2, msg2)

                val status1 = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId1)
                val status2 = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId2)

                status1.messageCount shouldBe 1
                status2.messageCount shouldBe 1
                session1.id shouldNotBe session2.id

                memoryService.clearSession(SessionLevel.TASK_DETAIL, taskId1)

                val status1AfterClear = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId1)
                val status2AfterClear = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId2)

                status1AfterClear.messageCount shouldBe 0
                status2AfterClear.messageCount shouldBe 1
            }
        }

        "persists messages across service restarts" {
            runTest {
                // create and add messages
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "Первое сообщение")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Второе сообщение")

                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

                // simulate restart — new service, same repository
                val newMemoryService = MemoryService(repository, taskRepo, taskStepRepo, factRepo)

                val restoredSession = newMemoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                restoredSession.id shouldBe session.id
                restoredSession.messages shouldHaveSize 2
                restoredSession.messages[0].id shouldBe msg1.id
                restoredSession.messages[1].id shouldBe msg2.id
            }
        }
    }
})
