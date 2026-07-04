package io.averkhogliad.ai.challenge.week4.cli.unit.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.chat.*
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import java.util.*

/**
 * Тесты для [ChatCommandHandler] — обработчика команд управления чатами.
 */
class ChatCommandHandlerTest : FreeSpec({

    lateinit var chatSessionManager: ChatSessionManager
    lateinit var repository: ChatSessionRepository
    lateinit var handler: ChatCommandHandler

    beforeEach {
        chatSessionManager = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        mockkObject(ChatListRenderer)
        mockkObject(ChatNotificationRenderer)
        mockkObject(ChatHistoryRenderer)

        handler = ChatCommandHandler(
            chatSessionManager = chatSessionManager,
            repository = repository,
            listRenderer = ChatListRenderer,
            notificationRenderer = ChatNotificationRenderer,
            historyRenderer = ChatHistoryRenderer
        )
    }

    afterEach {
        unmockkObject(ChatListRenderer)
        unmockkObject(ChatNotificationRenderer)
        unmockkObject(ChatHistoryRenderer)
    }

    fun createSession(id: UUID = UUID.randomUUID(), name: String = "Test Chat"): ChatSession =
        ChatSession.create(name = name, config = ChatConfig()).let {
            it.copy(metadata = it.metadata.copy(id = id))
        }

    // ═══════════════════════════════════════════════════════════════
    // ChatCommand.New
    // ═══════════════════════════════════════════════════════════════

    "ChatCommand.New" - {
        "should create session and update state" {
            runTest {
                // given
                val session = createSession()
                coEvery { chatSessionManager.createSession() } returns session
                val state = CliState()

                // when
                val result = handler.handle(ChatCommand.New, state)

                // then
                result.activeChatSessionId shouldBe session.metadata.id.toString()
                coVerify { chatSessionManager.createSession() }
                verify { ChatNotificationRenderer.renderChatCreated(session.metadata.name, session.metadata.id) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatCommand.List
    // ═══════════════════════════════════════════════════════════════

    "ChatCommand.List" - {
        "should list sessions and render" {
            runTest {
                // given
                val sessions = listOf(createSession(), createSession())
                coEvery { chatSessionManager.listSessions() } returns sessions
                val state = CliState()

                // when
                val result = handler.handle(ChatCommand.List, state)

                // then
                result shouldBe state // state не меняется
                coVerify { chatSessionManager.listSessions() }
                verify { ChatListRenderer.render(sessions, null) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatCommand.Switch
    // ═══════════════════════════════════════════════════════════════

    "ChatCommand.Switch" - {
        "should switch to session" {
            runTest {
                // given
                val id = UUID.randomUUID()
                val session = createSession(id, "Target Chat")
                coEvery { chatSessionManager.switchToSession(id) } returns session
                val state = CliState()

                // when
                val result = handler.handle(ChatCommand.Switch(id.toString()), state)

                // then
                result.activeChatSessionId shouldBe id.toString()
                coVerify { chatSessionManager.switchToSession(id) }
                verify { ChatNotificationRenderer.renderSwitchedTo(session.metadata.name, id) }
            }
        }

        "should render error on invalid UUID" {
            runTest {
                // given
                val state = CliState()

                // when
                val result = handler.handle(ChatCommand.Switch("not-a-uuid"), state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Неверный формат ID: not-a-uuid") }
            }
        }

        "should render error when session not found" {
            runTest {
                // given
                val id = UUID.randomUUID()
                coEvery { chatSessionManager.switchToSession(id) } throws IllegalStateException("Сессия не найдена: $id")
                val state = CliState()

                // when
                val result = handler.handle(ChatCommand.Switch(id.toString()), state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Сессия не найдена: $id") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatCommand.Rename
    // ═══════════════════════════════════════════════════════════════

    "ChatCommand.Rename" - {
        "should rename active session" {
            runTest {
                // given
                val id = UUID.randomUUID()
                val session = createSession(id, "Renamed Chat")
                coEvery { chatSessionManager.renameSession(id, "Renamed Chat") } returns session
                val state = CliState(activeChatSessionId = id.toString())

                // when
                val result = handler.handle(ChatCommand.Rename("Renamed Chat"), state)

                // then
                result shouldBe state
                coVerify { chatSessionManager.renameSession(id, "Renamed Chat") }
                verify { ChatNotificationRenderer.renderChatRenamed("Renamed Chat") }
            }
        }

        "should render error when no active chat" {
            runTest {
                // given
                val state = CliState() // no activeChatSessionId

                // when
                val result = handler.handle(ChatCommand.Rename("New Name"), state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Нет активного чата для переименования") }
            }
        }

        "should render error on invalid UUID" {
            runTest {
                // given
                val state = CliState(activeChatSessionId = "not-a-uuid")

                // when
                val result = handler.handle(ChatCommand.Rename("New Name"), state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Неверный формат ID: not-a-uuid") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatCommand.Delete
    // ═══════════════════════════════════════════════════════════════

    "ChatCommand.Delete" - {
        "should delete session (not active)" {
            runTest {
                // given
                val id = UUID.randomUUID()
                coEvery { chatSessionManager.deleteSession(id) } returns Unit
                val state = CliState(activeChatSessionId = UUID.randomUUID().toString()) // другой активный чат

                // when
                val result = handler.handle(ChatCommand.Delete(id.toString()), state)

                // then
                result shouldBe state
                coVerify { chatSessionManager.deleteSession(id) }
                verify { ChatNotificationRenderer.renderChatDeleted(id) }
            }
        }

        "should delete active session and update active" {
            runTest {
                // given
                val id = UUID.randomUUID()
                val newActive = createSession(UUID.randomUUID(), "New Active")
                coEvery { chatSessionManager.deleteSession(id) } returns Unit
                coEvery { chatSessionManager.getActiveSession() } returns newActive
                val state = CliState(activeChatSessionId = id.toString())

                // when
                val result = handler.handle(ChatCommand.Delete(id.toString()), state)

                // then
                result.activeChatSessionId shouldBe newActive.metadata.id.toString()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatCommand.Archive
    // ═══════════════════════════════════════════════════════════════

    "ChatCommand.Archive" - {
        "should archive active session" {
            runTest {
                // given
                val id = UUID.randomUUID()
                val newActive = createSession(UUID.randomUUID(), "New Active")
                coEvery { chatSessionManager.archiveSession(id) } returns Unit
                coEvery { chatSessionManager.getActiveSession() } returns newActive
                val state = CliState(activeChatSessionId = id.toString())

                // when
                val result = handler.handle(ChatCommand.Archive, state)

                // then
                result.activeChatSessionId shouldBe newActive.metadata.id.toString()
                coVerify { chatSessionManager.archiveSession(id) }
                verify { ChatNotificationRenderer.renderChatArchived(id) }
            }
        }

        "should render error when no active chat" {
            runTest {
                // given
                val state = CliState()

                // when
                val result = handler.handle(ChatCommand.Archive, state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Нет активного чата для архивации") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatCommand.History
    // ═══════════════════════════════════════════════════════════════

    "ChatCommand.History" - {
        "should render history" {
            runTest {
                // given
                val id = UUID.randomUUID()
                val session = createSession(id)
                coEvery { repository.loadById(id) } returns Result.success(session)
                val state = CliState(activeChatSessionId = id.toString())

                // when
                val result = handler.handle(ChatCommand.History(5), state)

                // then
                result shouldBe state
                coVerify { repository.loadById(id) }
                verify { ChatHistoryRenderer.render(session.messages, 5) }
            }
        }

        "should render error when no active chat" {
            runTest {
                // given
                val state = CliState()

                // when
                val result = handler.handle(ChatCommand.History(10), state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Нет активного чата для просмотра истории") }
            }
        }

        "should render error when session not found" {
            runTest {
                // given
                val id = UUID.randomUUID()
                coEvery { repository.loadById(id) } returns Result.success(null)
                val state = CliState(activeChatSessionId = id.toString())

                // when
                val result = handler.handle(ChatCommand.History(10), state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Ошибка при отображении истории: Сессия не найдена: $id") }
            }
        }
    }
})
