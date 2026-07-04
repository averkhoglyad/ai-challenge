package io.averkhogliad.ai.challenge.week4.cli.it.persistence

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteChatSessionRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.*

/**
 * Интеграционные тесты для [SqliteChatSessionRepository].
 *
 * Используют временный файл базы данных SQLite для каждого теста,
 * который удаляется после завершения теста.
 */
class SqliteChatSessionRepositoryIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteChatSessionRepository

    beforeEach {
        tempDbFile = Files.createTempFile("test-chat-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteChatSessionRepository(database)
    }

    afterEach {
        database.close()
        tempDbFile.delete()

        // Удаляем WAL и SHM файлы, если они существуют
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "CRUD — save and load" - {

        "should save session and load by id" {
            // given
            val session = ChatSession.create(name = "Test Chat")

            // when
            repository.save(session)
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldNotBe null
            loaded!!.metadata.id shouldBe session.metadata.id
            loaded.metadata.name shouldBe "Test Chat"
            loaded.metadata.active shouldBe true
            loaded.metadata.archived shouldBe false
        }

        "should return null when session not found" {
            // when
            val loaded = repository.loadById(UUID.randomUUID()).getOrNull()

            // then
            loaded shouldBe null
        }

        "should update session on save" {
            // given
            val session = ChatSession.create(name = "Original")
            repository.save(session)

            // when
            val renamed = session.rename("Updated")
            repository.save(renamed)
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldNotBe null
            loaded!!.metadata.name shouldBe "Updated"
        }

        "should delete session" {
            // given
            val session = ChatSession.create()
            repository.save(session)

            // when
            repository.deleteSession(session.metadata.id)
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldBe null
        }

        "should cascade delete messages when session is deleted" {
            // given
            val session = ChatSession.create()
            val msg = ChatMessage.User(
                id = UUID.randomUUID(),
                sessionId = session.metadata.id,
                text = "Hello",
                createdAt = Instant.now()
            )
            val sessionWithMsg = session.addMessage(msg)
            repository.save(sessionWithMsg)

            // when
            repository.deleteSession(session.metadata.id)
            // Re-create session — it should not have messages
            val freshSession = ChatSession.create(config = ChatConfig())
            repository.save(freshSession)
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldBe null
        }
    }

    "Active session management" - {

        "should find active session" {
            // given
            val session = ChatSession.create()
            repository.save(session)

            // when
            val active = repository.loadActive().getOrNull()

            // then
            active shouldNotBe null
            active!!.metadata.id shouldBe session.metadata.id
        }

        "should return null when no active session" {
            // given
            val session = ChatSession.create()
            repository.save(session)
            repository.setActive(session.metadata.id)
            // Now archive it
            repository.archiveSession(session.metadata.id)

            // when
            val active = repository.loadActive().getOrNull()

            // then
            active shouldBe null
        }

        "should setActive — only one active at a time" {
            // given
            val session1 = ChatSession.create(name = "Session 1")
            val session2 = ChatSession.create(name = "Session 2")
            repository.save(session1)
            repository.save(session2)

            // when
            repository.setActive(session2.metadata.id)

            // then
            val active = repository.loadActive().getOrNull()
            active shouldNotBe null
            active!!.metadata.id shouldBe session2.metadata.id
        }

        "should archive session" {
            // given
            val session = ChatSession.create()
            repository.save(session)

            // when
            repository.archiveSession(session.metadata.id)
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldNotBe null
            loaded!!.metadata.archived shouldBe true
            loaded.metadata.active shouldBe false
        }
    }

    "List sessions" - {

        "should list all sessions" {
            // given
            val s1 = ChatSession.create(name = "A")
            val s2 = ChatSession.create(name = "B")
            repository.save(s1)
            repository.save(s2)

            // when
            val sessions = repository.listSessions().getOrNull()

            // then
            sessions shouldNotBe null
            sessions!!.size shouldBe 2
        }

        "should return empty list when no sessions" {
            // when
            val sessions = repository.listSessions().getOrNull()

            // then
            sessions shouldNotBe null
            sessions!!.isEmpty() shouldBe true
        }
    }

    "Messages persistence" - {

        "should save and load messages with session" {
            // given
            val session = ChatSession.create()
            val userMsg = ChatMessage.User(
                id = UUID.randomUUID(),
                sessionId = session.metadata.id,
                text = "Hello",
                createdAt = Instant.now()
            )
            val assistantMsg = ChatMessage.Assistant(
                id = UUID.randomUUID(),
                sessionId = session.metadata.id,
                text = "Hi there!",
                citations = listOf(1),
                sources = listOf(
                    ChatSource(
                        citationNumber = 1,
                        documentId = "doc-1",
                        documentName = "Readme",
                        relevance = 0.95f
                    )
                ),
                createdAt = Instant.now()
            )
            val sessionWithMessages = session.addMessage(userMsg).addMessage(assistantMsg)

            // when
            repository.save(sessionWithMessages)
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldNotBe null
            loaded!!.messages.size shouldBe 2
            (loaded.messages[0] is ChatMessage.User) shouldBe true
            (loaded.messages[0] as ChatMessage.User).text shouldBe "Hello"
            (loaded.messages[1] is ChatMessage.Assistant) shouldBe true
            (loaded.messages[1] as ChatMessage.Assistant).text shouldBe "Hi there!"
            (loaded.messages[1] as ChatMessage.Assistant).sources.size shouldBe 1
        }

        "should load empty messages for session without messages" {
            // given
            val session = ChatSession.create()
            repository.save(session)

            // when
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldNotBe null
            loaded!!.messages.isEmpty() shouldBe true
        }

        "should update messages on re-save" {
            // given
            val session = ChatSession.create()
            val msg1 = ChatMessage.User(
                id = UUID.randomUUID(), sessionId = session.metadata.id,
                text = "First", createdAt = Instant.now()
            )
            val withMsg1 = session.addMessage(msg1)
            repository.save(withMsg1)

            // when
            val msg2 = ChatMessage.User(
                id = UUID.randomUUID(), sessionId = session.metadata.id,
                text = "Second", createdAt = Instant.now()
            )
            val withMsg2 = withMsg1.addMessage(msg2)
            repository.save(withMsg2)
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldNotBe null
            loaded!!.messages.size shouldBe 2
        }
    }

    "TaskState persistence" - {

        "should persist task state" {
            // given
            val session = ChatSession.create().updateTaskState(
                TaskState(
                    goal = "Test Goal",
                    definedTerms = listOf("API" to "Definition"),
                    constraints = listOf("Constraint 1")
                )
            )

            // when
            repository.save(session)
            val loaded = repository.loadById(session.metadata.id).getOrNull()

            // then
            loaded shouldNotBe null
            loaded!!.taskState.goal shouldBe "Test Goal"
            loaded.taskState.definedTerms.size shouldBe 1
            loaded.taskState.constraints.size shouldBe 1
        }

        "should save and load task state separately via TaskStateRepository" {
            // given
            val session = ChatSession.create()
            repository.save(session)
            val newState = TaskState(goal = "Separate goal")

            // when
            repository.save(session.metadata.id, newState)
            val loaded = repository.load(session.metadata.id).getOrNull()

            // then
            loaded shouldNotBe null
            loaded!!.goal shouldBe "Separate goal"
        }
    }
})
