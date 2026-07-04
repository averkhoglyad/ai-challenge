package io.averkhogliad.ai.challenge.week4.cli.unit.application.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatNameGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.*

/**
 * Тесты для [ChatSessionManager] — управление множественными чат-сессиями.
 *
 * Используем Fake-реализацию репозитория для unit-тестов.
 */
class ChatSessionManagerTest : FreeSpec({

    /**
     * In-memory fake реализация [ChatSessionRepository].
     */
    class FakeChatSessionRepository : ChatSessionRepository {
        private val sessions = mutableMapOf<UUID, ChatSession>()

        override suspend fun save(session: ChatSession): Result<ChatSession> {
            sessions[session.metadata.id] = session
            return Result.success(session)
        }

        override suspend fun loadActive(): Result<ChatSession?> {
            val active = sessions.values.find { it.metadata.active && !it.metadata.archived }
            return Result.success(active)
        }

        override suspend fun loadById(id: UUID): Result<ChatSession?> {
            return Result.success(sessions[id])
        }

        override suspend fun listSessions(): Result<List<ChatSession>> {
            return Result.success(sessions.values.toList())
        }

        override suspend fun setActive(id: UUID): Result<Unit> {
            sessions.values.forEach { session ->
                if (session.metadata.id in sessions) {
                    val s = sessions[session.metadata.id]!!
                    sessions[session.metadata.id] = s.copy(
                        metadata = s.metadata.copy(active = s.metadata.id == id)
                    )
                }
            }
            val target = sessions[id] ?: return Result.failure(NoSuchElementException("Not found: $id"))
            sessions[id] = target.copy(metadata = target.metadata.copy(active = true))
            return Result.success(Unit)
        }

        override suspend fun archiveSession(id: UUID): Result<Unit> {
            val session = sessions[id] ?: return Result.failure(NoSuchElementException("Not found: $id"))
            sessions[id] = session.copy(
                metadata = session.metadata.copy(archived = true, active = false)
            )
            return Result.success(Unit)
        }

        override suspend fun deleteSession(id: UUID): Result<Unit> {
            sessions.remove(id)
            return Result.success(Unit)
        }
    }

    fun createManager(
        repository: ChatSessionRepository = FakeChatSessionRepository(),
        nameGenerator: ChatNameGenerator = mockk(relaxed = true)
    ) = ChatSessionManager(
        repository = repository,
        nameGenerator = nameGenerator,
        config = ChatConfig()
    )

    "Создание сессии" - {

        "should create active non-archived session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)

                // when
                val session = manager.createSession("Test Chat")

                // then
                session.metadata.name shouldBe "Test Chat"
                session.metadata.active shouldBe true
                session.metadata.archived shouldBe false
                session.messages.isEmpty() shouldBe true
            }
        }

        "should archive previous active session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)
                val first = manager.createSession("First")

                // when
                val second = manager.createSession("Second")

                // then
                val firstAfter = fakeRepo.loadById(first.metadata.id).getOrNull()
                firstAfter?.metadata?.archived shouldBe true
                firstAfter?.metadata?.active shouldBe false
                second.metadata.active shouldBe true
            }
        }
    }

    "Переключение между сессиями" - {

        "should switch active to target session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)
                val first = manager.createSession("First")
                val second = manager.createSession("Second")

                // when
                val activated = manager.switchToSession(first.metadata.id)

                // then
                activated.metadata.active shouldBe true
                activated.metadata.id shouldBe first.metadata.id
            }
        }

        "should throw when switching to non-existent session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)

                // when & then
                try {
                    manager.switchToSession(UUID.randomUUID())
                } catch (e: IllegalStateException) {
                    // expected
                }
            }
        }
    }

    "List sessions" - {

        "should return all sessions" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)
                manager.createSession("A")
                manager.createSession("B")

                // when
                val sessions = manager.listSessions()

                // then
                sessions.size shouldBe 2
            }
        }

        "should return empty list when no sessions" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)

                // when
                val sessions = manager.listSessions()

                // then
                sessions.isEmpty() shouldBe true
            }
        }
    }

    "Удаление сессии" - {

        "should delete session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)
                val session = manager.createSession("To Delete")

                // when
                manager.deleteSession(session.metadata.id)

                // then
                val found = fakeRepo.loadById(session.metadata.id).getOrNull()
                found shouldBe null
            }
        }

        "should activate last non-archived when deleting active session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)
                // Create two sessions without archiving via manager
                val first = ChatSession.create(name = "First", config = ChatConfig())
                fakeRepo.save(first)
                val second = ChatSession.create(name = "Second", config = ChatConfig())
                fakeRepo.save(second)
                fakeRepo.setActive(second.metadata.id)
                // second is active now

                // when
                manager.deleteSession(second.metadata.id)

                // then — first should become active again
                val sessions = manager.listSessions()
                val activeSession = sessions.find { it.metadata.active }
                activeSession shouldNotBe null
                if (activeSession != null) {
                    activeSession.metadata.id shouldBe first.metadata.id
                }
            }
        }
    }

    "Get active session" - {

        "should return active session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)
                val session = manager.createSession("Active")

                // when
                val active = manager.getActiveSession()

                // then
                active shouldNotBe null
                active?.metadata?.id shouldBe session.metadata.id
            }
        }

        "should return null when no sessions" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)

                // when
                val active = manager.getActiveSession()

                // then
                active shouldBe null
            }
        }
    }

    "Rename session" - {

        "should rename session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)
                val session = manager.createSession("Original")

                // when
                val renamed = manager.renameSession(session.metadata.id, "Renamed")

                // then
                renamed.metadata.name shouldBe "Renamed"
                renamed.metadata.nameGenerated shouldBe false
            }
        }

        "should throw when renaming non-existent session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)

                // when & then
                try {
                    manager.renameSession(UUID.randomUUID(), "New Name")
                } catch (e: IllegalStateException) {
                    // expected
                }
            }
        }
    }

    "Archive session" - {

        "should archive session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val manager = createManager(repository = fakeRepo)
                val session = manager.createSession("To Archive")

                // when
                manager.archiveSession(session.metadata.id)

                // then
                val archived = fakeRepo.loadById(session.metadata.id).getOrNull()
                archived?.metadata?.archived shouldBe true
                archived?.metadata?.active shouldBe false
            }
        }
    }

    "maybeAutoName" - {

        "should auto-name after user+assistant exchange" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val nameGenerator = mockk<ChatNameGenerator>()
                val manager = createManager(repository = fakeRepo, nameGenerator = nameGenerator)
                val session = manager.createSession("New Chat")

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
                    citations = emptyList(),
                    sources = emptyList(),
                    createdAt = Instant.now()
                )
                val withMessages = session.addMessage(userMsg).addMessage(assistantMsg)

                coEvery { nameGenerator.generate(any()) } returns Result.success("Greeting Chat")

                // when
                val result = manager.maybeAutoName(withMessages)

                // then
                result.metadata.name shouldBe "Greeting Chat"
                result.metadata.nameGenerated shouldBe true
            }
        }

        "should not auto-name when disabled in config" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val nameGenerator = mockk<ChatNameGenerator>()
                val manager = ChatSessionManager(
                    repository = fakeRepo,
                    nameGenerator = nameGenerator,
                    config = ChatConfig(autoNameEnabled = false)
                )
                val session = ChatSession.create(config = ChatConfig(autoNameEnabled = false))
                val userMsg = ChatMessage.User(
                    id = UUID.randomUUID(), sessionId = session.metadata.id,
                    text = "Hello", createdAt = Instant.now()
                )
                val assistantMsg = ChatMessage.Assistant(
                    id = UUID.randomUUID(), sessionId = session.metadata.id,
                    text = "Hi", citations = emptyList(), sources = emptyList(),
                    createdAt = Instant.now()
                )
                val withMessages = session.addMessage(userMsg).addMessage(assistantMsg)

                // when
                val result = manager.maybeAutoName(withMessages)

                // then
                result.metadata.name shouldBe "New Chat"
                coVerify(exactly = 0) { nameGenerator.generate(any()) }
            }
        }

        "should keep name when generator fails" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val nameGenerator = mockk<ChatNameGenerator>()
                val manager = createManager(repository = fakeRepo, nameGenerator = nameGenerator)
                val session = ChatSession.create()
                val userMsg = ChatMessage.User(
                    id = UUID.randomUUID(), sessionId = session.metadata.id,
                    text = "Hello", createdAt = Instant.now()
                )
                val assistantMsg = ChatMessage.Assistant(
                    id = UUID.randomUUID(), sessionId = session.metadata.id,
                    text = "Hi", citations = emptyList(), sources = emptyList(),
                    createdAt = Instant.now()
                )
                val withMessages = session.addMessage(userMsg).addMessage(assistantMsg)

                coEvery { nameGenerator.generate(any()) } throws RuntimeException("Down")

                // when
                val result = manager.maybeAutoName(withMessages)

                // then
                result.metadata.name shouldBe "New Chat"
            }
        }
    }
})
