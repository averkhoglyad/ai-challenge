package io.averkhogliad.ai.challenge.week4.cli.unit.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.DialogSessionRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryService
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest

/**
 * Тесты для [MemoryService] — сервиса управления памятью диалога.
 */
class MemoryServiceTest : FreeSpec({

    lateinit var repository: InMemoryDialogSessionRepository
    lateinit var memoryService: MemoryService

    beforeEach {
        repository = InMemoryDialogSessionRepository()
        memoryService = MemoryService(repository)
    }

    "getSessionForLevel" - {

        "should create new session for TASK_LIST level" {
            runTest {
                // given
                // repository is empty

                // when
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)

                // then
                session shouldNotBe null
                session.level shouldBe SessionLevel.TASK_LIST
                session.taskId shouldBe null
                session.messages.isEmpty() shouldBe true
            }
        }

        "should create new session for TASK_DETAIL level" {
            runTest {
                // given
                val taskId = TaskId("task-1")

                // when
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId)

                // then
                session shouldNotBe null
                session.level shouldBe SessionLevel.TASK_DETAIL
                session.taskId shouldBe taskId
            }
        }

        "should return existing session if already created" {
            runTest {
                // given
                val session1 = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)

                // when
                val session2 = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)

                // then
                session1.id shouldBe session2.id
            }
        }

        "should return different sessions for different tasks" {
            runTest {
                // given
                val session1 = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, TaskId("task-1"))

                // when
                val session2 = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, TaskId("task-2"))

                // then
                (session1.id != session2.id) shouldBe true
            }
        }
    }

    "addMessageToSession" - {

        "should add message to session" {
            runTest {
                // given
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                val message = Message.create(session.id, MessageRole.USER, "Hello")

                // when
                val updatedSession = memoryService.addMessageToSession(
                    SessionLevel.TASK_LIST,
                    null,
                    message
                )

                // then
                updatedSession.messages.size shouldBe 1
                updatedSession.messages[0] shouldBe message
            }
        }

        "should add multiple messages" {
            runTest {
                // given
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)

                // when
                val updatedSession = memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

                // then
                updatedSession.messages.size shouldBe 2
            }
        }
    }

    "clearSession" - {

        "should clear all messages from session" {
            runTest {
                // given
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

                // when
                val clearedSession = memoryService.clearSession(SessionLevel.TASK_LIST)

                // then
                clearedSession.messages.isEmpty() shouldBe true
            }
        }
    }

    "getRecentMessages" - {

        "should return recent messages with default limit" {
            runTest {
                // given
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
                val msg3 = Message.create(session.id, MessageRole.USER, "Third")

                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)
                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg3)

                // when
                val recent = memoryService.getRecentMessages(SessionLevel.TASK_LIST)

                // then
                recent.size shouldBe 3
            }
        }

        "should return limited number of recent messages" {
            runTest {
                // given
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
                val msg3 = Message.create(session.id, MessageRole.USER, "Third")

                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)
                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg3)

                // when
                val recent = memoryService.getRecentMessages(SessionLevel.TASK_LIST, limit = 2)

                // then
                recent.size shouldBe 2
                recent[0] shouldBe msg2
                recent[1] shouldBe msg3
            }
        }
    }

    "getMemoryStatus" - {

        "should return memory status with correct message count" {
            runTest {
                // given
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg1)
                memoryService.addMessageToSession(SessionLevel.TASK_LIST, null, msg2)

                // when
                val status = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)

                // then
                status.sessionId shouldBe session.id
                status.level shouldBe SessionLevel.TASK_LIST
                status.messageCount shouldBe 2
            }
        }

        "should return status for TASK_DETAIL level" {
            runTest {
                // given
                val taskId = TaskId("task-1")
                val session = memoryService.getSessionForLevel(SessionLevel.TASK_DETAIL, taskId)
                val message = Message.create(session.id, MessageRole.USER, "Hello")

                memoryService.addMessageToSession(SessionLevel.TASK_DETAIL, taskId, message)

                // when
                val status = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, taskId)

                // then
                status.taskId shouldBe taskId
                status.messageCount shouldBe 1
            }
        }
    }

    "switchToTaskLevel" - {

        "should switch to TASK_DETAIL level" {
            runTest {
                // given
                val taskId = TaskId("task-1")

                // when
                val session = memoryService.switchToTaskLevel(taskId)

                // then
                session.level shouldBe SessionLevel.TASK_DETAIL
                session.taskId shouldBe taskId
            }
        }
    }

    "switchToTaskListLevel" - {

        "should switch to TASK_LIST level" {
            runTest {
                // when
                val session = memoryService.switchToTaskListLevel()

                // then
                session.level shouldBe SessionLevel.TASK_LIST
                session.taskId shouldBe null
            }
        }
    }
}) {
    /**
     * In-memory реализация DialogSessionRepository для тестирования.
     */
    private class InMemoryDialogSessionRepository : DialogSessionRepository {
        private val sessions = mutableMapOf<String, DialogSession>()

        override fun save(session: DialogSession): DialogSession {
            sessions[session.id.value] = session
            return session
        }

        override fun findById(id: SessionId): DialogSession? {
            return sessions[id.value]
        }

        override fun findByTaskId(taskId: TaskId): DialogSession? {
            return sessions.values.find { it.taskId == taskId }
        }

        override fun findActiveSession(): DialogSession? {
            return sessions.values.maxByOrNull { it.updatedAt }
        }

        override fun delete(id: SessionId) {
            sessions.remove(id.value)
        }
    }
}
