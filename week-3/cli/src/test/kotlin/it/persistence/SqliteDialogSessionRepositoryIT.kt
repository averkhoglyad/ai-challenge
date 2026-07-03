package io.averkhogliad.ai.challenge.week3.cli.it.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDialogSessionRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files

/**
 * Интеграционные тесты для [SqliteDialogSessionRepository].
 *
 * Используют временный файл базы данных для каждого теста,
 * который удаляется после завершения теста.
 */
class SqliteDialogSessionRepositoryIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteDialogSessionRepository

    beforeTest {
        tempDbFile = Files.createTempFile("test-dialog-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteDialogSessionRepository(database)
    }

    afterTest {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "Сохранение и поиск сессий" - {
        "should save and find session by id" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)

            repository.save(session)
            val found = repository.findById(session.id)

            found shouldNotBe null
            found!!.id shouldBe session.id
            found.level shouldBe session.level
            found.taskId shouldBe session.taskId
            found.createdAt shouldBe session.createdAt
        }

        "should return null when session not found" {
            val nonExistentId = SessionId.generate()
            val found = repository.findById(nonExistentId)

            found shouldBe null
        }

        "should save session with messages" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "Hello")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Hi there")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

            repository.save(sessionWithMessages)
            val found = repository.findById(session.id)

            found shouldNotBe null
            found!!.messages shouldHaveSize 2
            found.messages[0].id shouldBe msg1.id
            found.messages[1].id shouldBe msg2.id
        }

        "should update session on save" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            val message = Message.create(session.id, MessageRole.USER, "Hello")
            val updatedSession = session.addMessage(message)
            repository.save(updatedSession)

            val found = repository.findById(session.id)
            found shouldNotBe null
            found!!.messages shouldHaveSize 1
        }
    }

    "Поиск по taskId" - {
        "should find session by taskId" {
            val taskId = TaskId("task-1")
            val session = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)

            repository.save(session)
            val found = repository.findByTaskId(taskId)

            found shouldNotBe null
            found!!.id shouldBe session.id
            found.taskId shouldBe taskId
        }

        "should return null when no session with taskId" {
            val nonExistentTaskId = TaskId("non-existent")
            val found = repository.findByTaskId(nonExistentTaskId)

            found shouldBe null
        }

        "should find most recent session when multiple exist for same taskId" {
            val taskId = TaskId("task-1")
            val session1 = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)
            repository.save(session1)

            Thread.sleep(1)

            val session2 = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)
            repository.save(session2)

            val found = repository.findByTaskId(taskId)
            found shouldNotBe null
            found!!.id shouldBe session2.id
        }
    }

    "Поиск активной сессии" - {
        "should find active session" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")
            val activeSession = session.addMessage(message)

            repository.save(activeSession)
            val found = repository.findActiveSession()

            found shouldNotBe null
            found!!.id shouldBe session.id
        }

        "should return null when no active sessions" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            val found = repository.findActiveSession()
            found shouldBe null
        }

        "should find most recently updated active session" {
            val session1 = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session1.id, MessageRole.USER, "First")
            repository.save(session1.addMessage(msg1))

            Thread.sleep(1)

            val session2 = DialogSession.create(SessionLevel.TASK_LIST)
            val msg2 = Message.create(session2.id, MessageRole.USER, "Second")
            repository.save(session2.addMessage(msg2))

            val found = repository.findActiveSession()
            found shouldNotBe null
            found!!.id shouldBe session2.id
        }
    }

    "Удаление сессий" - {
        "should delete session by id" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            repository.delete(session.id)
            val found = repository.findById(session.id)

            found shouldBe null
        }

        "should delete session with messages" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "Hello")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Hi")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

            repository.save(sessionWithMessages)
            repository.delete(session.id)

            val found = repository.findById(session.id)
            found shouldBe null
        }

        "should not throw when deleting non-existent session" {
            val nonExistentId = SessionId.generate()
            repository.delete(nonExistentId)
            // Не должно выбрасывать исключение
        }
    }

    "Транзакционность" - {
        "should save session and messages atomically" {
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

            found shouldNotBe null
            found!!.messages shouldHaveSize 10
        }

        "should handle multiple saves correctly" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val session1 = session.addMessage(msg1)
            repository.save(session1)

            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val session2 = session1.addMessage(msg2)
            repository.save(session2)

            val found = repository.findById(session.id)
            found shouldNotBe null
            found!!.messages shouldHaveSize 2
        }
    }

    "Разные уровни сессий" - {
        "should save and retrieve TASK_LIST session" {
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            repository.save(session)

            val found = repository.findById(session.id)
            found shouldNotBe null
            found!!.level shouldBe SessionLevel.TASK_LIST
            found.taskId shouldBe null
        }

        "should save and retrieve TASK_DETAIL session" {
            val taskId = TaskId("task-1")
            val session = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)
            repository.save(session)

            val found = repository.findById(session.id)
            found shouldNotBe null
            found!!.level shouldBe SessionLevel.TASK_DETAIL
            found.taskId shouldBe taskId
        }
    }
})
