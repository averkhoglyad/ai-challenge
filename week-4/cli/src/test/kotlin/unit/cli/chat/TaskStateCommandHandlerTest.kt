package io.averkhogliad.ai.challenge.week4.cli.unit.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.TaskStateManager
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatNotificationRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.chat.TaskStateCommand
import io.averkhogliad.ai.challenge.week4.cli.cli.chat.TaskStateCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.chat.TaskStateRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import java.util.*

/**
 * Тесты для [TaskStateCommandHandler] — обработчика команд управления памятью задачи.
 */
class TaskStateCommandHandlerTest : FreeSpec({

    lateinit var taskStateManager: TaskStateManager
    lateinit var handler: TaskStateCommandHandler

    val sessionId = UUID.randomUUID()
    val sessionIdStr = sessionId.toString()

    val emptyTaskState = TaskState.EMPTY

    beforeEach {
        taskStateManager = mockk(relaxed = true)
        mockkObject(TaskStateRenderer)
        mockkObject(ChatNotificationRenderer)

        handler = TaskStateCommandHandler(
            taskStateManager = taskStateManager,
            renderer = TaskStateRenderer,
            notificationRenderer = ChatNotificationRenderer
        )
    }

    afterEach {
        unmockkObject(TaskStateRenderer)
        unmockkObject(ChatNotificationRenderer)
    }

    fun stateWithActiveSession(): CliState =
        CliState(activeChatSessionId = sessionIdStr)

    // ═══════════════════════════════════════════════════════════════
    // TaskStateCommand.Show
    // ═══════════════════════════════════════════════════════════════

    "TaskStateCommand.Show" - {
        "should render task state" {
            runTest {
                // given
                coEvery { taskStateManager.getTaskState(sessionId) } returns emptyTaskState
                val state = stateWithActiveSession()

                // when
                val result = handler.handle(TaskStateCommand.Show, state)

                // then
                result shouldBe state
                coVerify { taskStateManager.getTaskState(sessionId) }
                verify { TaskStateRenderer.render(emptyTaskState) }
            }
        }

        "should render error when no active chat" {
            runTest {
                // given
                val state = CliState()

                // when
                val result = handler.handle(TaskStateCommand.Show, state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Нет активного чата") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TaskStateCommand.Reset
    // ═══════════════════════════════════════════════════════════════

    "TaskStateCommand.Reset" - {
        "should reset task state" {
            runTest {
                // given
                coEvery { taskStateManager.resetTaskState(sessionId) } returns Unit
                val state = stateWithActiveSession()

                // when
                val result = handler.handle(TaskStateCommand.Reset, state)

                // then
                result shouldBe state
                coVerify { taskStateManager.resetTaskState(sessionId) }
                verify { ChatNotificationRenderer.renderTaskStateReset() }
            }
        }

        "should render error when no active chat" {
            runTest {
                // given
                val state = CliState()

                // when
                val result = handler.handle(TaskStateCommand.Reset, state)

                // then
                result shouldBe state
                verify { ChatNotificationRenderer.renderError("Нет активного чата") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TaskStateCommand.SetGoal
    // ═══════════════════════════════════════════════════════════════

    "TaskStateCommand.SetGoal" - {
        "should set goal" {
            runTest {
                // given
                coEvery { taskStateManager.setGoal(sessionId, "Build a web app") } returns Unit
                val state = stateWithActiveSession()

                // when
                val result = handler.handle(TaskStateCommand.SetGoal("Build a web app"), state)

                // then
                result shouldBe state
                coVerify { taskStateManager.setGoal(sessionId, "Build a web app") }
                verify { ChatNotificationRenderer.renderTaskStateUpdated("Цель обновлена") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TaskStateCommand.AddTerm
    // ═══════════════════════════════════════════════════════════════

    "TaskStateCommand.AddTerm" - {
        "should add term" {
            runTest {
                // given
                coEvery { taskStateManager.addTerm(sessionId, "API", "Application Programming Interface") } returns Unit
                val state = stateWithActiveSession()

                // when
                val result = handler.handle(
                    TaskStateCommand.AddTerm("API", "Application Programming Interface"),
                    state
                )

                // then
                result shouldBe state
                coVerify { taskStateManager.addTerm(sessionId, "API", "Application Programming Interface") }
                verify { ChatNotificationRenderer.renderTaskStateUpdated("Термин 'API' добавлен") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TaskStateCommand.RemoveTerm
    // ═══════════════════════════════════════════════════════════════

    "TaskStateCommand.RemoveTerm" - {
        "should remove term" {
            runTest {
                // given
                coEvery { taskStateManager.removeTerm(sessionId, "API") } returns Unit
                val state = stateWithActiveSession()

                // when
                val result = handler.handle(TaskStateCommand.RemoveTerm("API"), state)

                // then
                result shouldBe state
                coVerify { taskStateManager.removeTerm(sessionId, "API") }
                verify { ChatNotificationRenderer.renderTaskStateUpdated("Термин 'API' удалён") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TaskStateCommand.AddConstraint
    // ═══════════════════════════════════════════════════════════════

    "TaskStateCommand.AddConstraint" - {
        "should add constraint" {
            runTest {
                // given
                coEvery { taskStateManager.addConstraint(sessionId, "Must be scalable") } returns Unit
                val state = stateWithActiveSession()

                // when
                val result = handler.handle(TaskStateCommand.AddConstraint("Must be scalable"), state)

                // then
                result shouldBe state
                coVerify { taskStateManager.addConstraint(sessionId, "Must be scalable") }
                verify { ChatNotificationRenderer.renderTaskStateUpdated("Ограничение добавлено") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TaskStateCommand.RemoveConstraint
    // ═══════════════════════════════════════════════════════════════

    "TaskStateCommand.RemoveConstraint" - {
        "should remove constraint" {
            runTest {
                // given
                coEvery { taskStateManager.removeConstraint(sessionId, 2) } returns Unit
                val state = stateWithActiveSession()

                // when
                val result = handler.handle(TaskStateCommand.RemoveConstraint(2), state)

                // then
                result shouldBe state
                coVerify { taskStateManager.removeConstraint(sessionId, 2) }
                verify { ChatNotificationRenderer.renderTaskStateUpdated("Ограничение #2 удалено") }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Без активного чата
    // ═══════════════════════════════════════════════════════════════

    "All commands without active session" - {
        "should render error for all mutation commands" {
            runTest {
                val state = CliState()
                val commands = listOf(
                    TaskStateCommand.Reset,
                    TaskStateCommand.SetGoal("goal"),
                    TaskStateCommand.AddTerm("t", "d"),
                    TaskStateCommand.RemoveTerm("t"),
                    TaskStateCommand.AddConstraint("c"),
                    TaskStateCommand.RemoveConstraint(0)
                )
                for (cmd in commands) {
                    handler.handle(cmd, state)
                }
                verify(exactly = 6) { ChatNotificationRenderer.renderError("Нет активного чата") }
            }
        }
    }
})
