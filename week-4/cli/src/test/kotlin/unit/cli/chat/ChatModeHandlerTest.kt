package io.averkhogliad.ai.challenge.week4.cli.unit.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatExecutor
import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatResult
import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.chat.*
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSource
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import java.util.*

/**
 * Тесты для [ChatModeHandler] — обработчика ввода в режиме чата.
 */
class ChatModeHandlerTest : FreeSpec({

    lateinit var chatExecutor: ChatExecutor
    lateinit var chatSessionManager: ChatSessionManager
    lateinit var chatCommandHandler: ChatCommandHandler
    lateinit var taskStateCommandHandler: TaskStateCommandHandler
    lateinit var handler: ChatModeHandler

    val sessionId = UUID.randomUUID()

    fun createSession(): ChatSession = ChatSession.create(config = ChatConfig()).let {
        it.copy(metadata = it.metadata.copy(id = sessionId))
    }

    beforeEach {
        chatExecutor = mockk(relaxed = true)
        chatSessionManager = mockk(relaxed = true)
        chatCommandHandler = mockk(relaxed = true)
        taskStateCommandHandler = mockk(relaxed = true)

        mockkObject(ChatAnswerRenderer)
        mockkObject(ChatNotificationRenderer)

        handler = ChatModeHandler(
            chatExecutor = chatExecutor,
            chatSessionManager = chatSessionManager,
            chatCommandHandler = chatCommandHandler,
            taskStateCommandHandler = taskStateCommandHandler,
            answerRenderer = ChatAnswerRenderer,
            notificationRenderer = ChatNotificationRenderer
        )
    }

    afterEach {
        unmockkObject(ChatAnswerRenderer)
        unmockkObject(ChatNotificationRenderer)
    }

    // ═══════════════════════════════════════════════════════════════
    // enterChatMode
    // ═══════════════════════════════════════════════════════════════

    "enterChatMode" - {
        "should use existing active session" {
            runTest {
                // given
                val session = createSession()
                coEvery { chatSessionManager.getActiveSession() } returns session
                val state = CliState()

                // when
                val result = handler.enterChatMode(state)

                // then
                result.chatMode shouldBe true
                result.activeChatSessionId shouldBe session.metadata.id.toString()
                verify { ChatNotificationRenderer.renderChatModeEntered(session.metadata.name) }
            }
        }

        "should create session when no active exists" {
            runTest {
                // given
                val session = createSession()
                coEvery { chatSessionManager.getActiveSession() } returns null
                coEvery { chatSessionManager.createSession() } returns session
                val state = CliState()

                // when
                val result = handler.enterChatMode(state)

                // then
                result.chatMode shouldBe true
                result.activeChatSessionId shouldBe session.metadata.id.toString()
                coVerify { chatSessionManager.createSession() }
                verify { ChatNotificationRenderer.renderChatModeEntered(session.metadata.name) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // exitChatMode
    // ═══════════════════════════════════════════════════════════════

    "exitChatMode" - {
        "should set chatMode=false and render notification" {
            // given
            val state = CliState(chatMode = true)

            // when
            val result = handler.exitChatMode(state)

            // then
            result.chatMode shouldBe false
            verify { ChatNotificationRenderer.renderChatModeExited() }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // handleCommand
    // ═══════════════════════════════════════════════════════════════

    "handleCommand" - {
        "should delegate chat command to ChatCommandHandler" {
            runTest {
                // given
                val expectedState = CliState()
                coEvery { chatCommandHandler.handle(ChatCommand.New, any()) } returns expectedState
                val state = CliState()

                // when
                val result = handler.handleCommand("chat-new", "", state)

                // then
                result shouldBe expectedState
                coVerify { chatCommandHandler.handle(ChatCommand.New, state) }
            }
        }

        "should delegate task-state command to TaskStateCommandHandler" {
            runTest {
                // given
                val expectedState = CliState()
                coEvery { taskStateCommandHandler.handle(TaskStateCommand.Show, any()) } returns expectedState
                val state = CliState()

                // when
                val result = handler.handleCommand("task-state", "", state)

                // then
                result shouldBe expectedState
                coVerify { taskStateCommandHandler.handle(TaskStateCommand.Show, state) }
            }
        }

        "should render error for unknown command" {
            runTest {
                // given
                val state = CliState()

                // when
                val result = handler.handleCommand("unknown-cmd", "", state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Неизвестная команда: :unknown-cmd") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // handleMessage
    // ═══════════════════════════════════════════════════════════════

    "handleMessage" - {
        "should execute pipeline and render answer" {
            runTest {
                // given
                val session = createSession()
                val chatResult = ChatResult(
                    session = session,
                    answer = "Hello, user!",
                    citations = emptyList(),
                    stateDelta = null,
                    taskStateUpdated = false
                )
                coEvery { chatExecutor.execute(any(), any(), any(), any()) } returns chatResult
                val state = CliState(activeChatSessionId = sessionId.toString())

                // when
                val result = handler.handleMessage("Hello", state)

                // then
                result shouldBe state
                coVerify { chatExecutor.execute("Hello", sessionId, any(), any()) }
                verify { ChatAnswerRenderer.renderAnswer("Hello, user!", emptyList()) }
            }
        }

        "should render answer with citations" {
            runTest {
                // given
                val session = createSession()
                val source = ChatSource(1, "doc-1", "test.txt", 0.95f)
                val chatResult = ChatResult(
                    session = session,
                    answer = "Answer with source",
                    citations = listOf(source),
                    stateDelta = null,
                    taskStateUpdated = false
                )
                coEvery { chatExecutor.execute(any(), any(), any(), any()) } returns chatResult
                val state = CliState(activeChatSessionId = sessionId.toString())

                // when
                val result = handler.handleMessage("Question", state)

                // then
                result shouldBe state
                verify { ChatAnswerRenderer.renderAnswer("Answer with source", listOf(source)) }
            }
        }

        "should render task state update notification" {
            runTest {
                // given
                val session = createSession()
                val delta = TaskStateDelta.SetGoal("New Goal")
                val chatResult = ChatResult(
                    session = session,
                    answer = "Done",
                    citations = emptyList(),
                    stateDelta = delta,
                    taskStateUpdated = true
                )
                coEvery { chatExecutor.execute(any(), any(), any(), any()) } returns chatResult
                val state = CliState(activeChatSessionId = sessionId.toString())

                // when
                val result = handler.handleMessage("Set goal", state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderTaskStateUpdated("Цель установлена") }
            }
        }

        "should enter chat mode when no active session" {
            runTest {
                // given
                val session = createSession()
                coEvery { chatSessionManager.getActiveSession() } returns session
                val state = CliState() // no activeChatSessionId

                // when
                val result = handler.handleMessage("Hello", state)

                // then
                result.chatMode shouldBe true
                result.activeChatSessionId shouldBe session.metadata.id.toString()
            }
        }

        "should render error when executor fails" {
            runTest {
                // given
                coEvery { chatExecutor.execute(any(), any(), any(), any()) } throws RuntimeException("LLM down")
                val state = CliState(activeChatSessionId = sessionId.toString())

                // when
                val result = handler.handleMessage("Hello", state)

                // then
                result shouldBe state
                verify { ChatAnswerRenderer.renderError("Ошибка выполнения: LLM down") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // getPrompt
    // ═══════════════════════════════════════════════════════════════

    "getPrompt" - {
        "should return default prompt when no active session" {
            runTest {
                val state = CliState()
                val prompt = handler.getPrompt(state)
                prompt shouldBe "[Chat] > "
            }
        }

        "should return session name in prompt" {
            runTest {
                val session = createSession().let {
                    it.copy(metadata = it.metadata.copy(name = "My Project"))
                }
                coEvery { chatSessionManager.getActiveSession() } returns session
                val state = CliState(activeChatSessionId = sessionId.toString())

                val prompt = handler.getPrompt(state)
                prompt shouldBe "[My Project] > "
            }
        }

        "should truncate long names" {
            runTest {
                val longName = "This is a very long chat name for testing"
                val session = createSession().let {
                    it.copy(metadata = it.metadata.copy(name = longName))
                }
                coEvery { chatSessionManager.getActiveSession() } returns session
                val state = CliState(activeChatSessionId = sessionId.toString())

                val prompt = handler.getPrompt(state)
                prompt shouldBe "[This is a very lo...] > "
            }
        }
    }
})
