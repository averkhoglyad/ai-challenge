package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Message
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MessageRole
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class DialogSessionTest : FreeSpec({

    "DialogSession" - {

        "Creation" - {

            "should create session with TASK_LIST level" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)

                session.level shouldBe SessionLevel.TASK_LIST
                session.taskId shouldBe null
                session.messages.isEmpty() shouldBe true
                session.id.value.isNotBlank() shouldBe true
            }

            "should create session with TASK_DETAIL level and taskId" {
                val taskId = TaskId("task-1")
                val session = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)

                session.level shouldBe SessionLevel.TASK_DETAIL
                session.taskId shouldBe taskId
                session.messages.isEmpty() shouldBe true
            }

            "should throw exception when TASK_DETAIL without taskId" {
                shouldThrow<IllegalArgumentException> {
                    DialogSession(
                        id = SessionId.generate(),
                        level = SessionLevel.TASK_DETAIL,
                        taskId = null,
                        messages = emptyList(),
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                }
            }
        }

        "Activity" - {

            "should be inactive when no messages" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                session.isActive() shouldBe false
            }

            "should be active when has messages" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val message = Message.create(session.id, MessageRole.USER, "Hello")
                val sessionWithMessage = session.addMessage(message)

                sessionWithMessage.isActive() shouldBe true
            }
        }

        "AddMessage" - {

            "should add message to session" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val message = Message.create(session.id, MessageRole.USER, "Hello")

                val updatedSession = session.addMessage(message)

                updatedSession.messages.size shouldBe 1
                updatedSession.messages[0] shouldBe message
            }

            "should throw exception when message sessionId doesn't match" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val otherSessionId = SessionId.generate()
                val message = Message.create(otherSessionId, MessageRole.USER, "Hello")

                shouldThrow<IllegalArgumentException> {
                    session.addMessage(message)
                }
            }

            "should update updatedAt when adding message" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val originalUpdatedAt = session.updatedAt
                val message = Message.create(session.id, MessageRole.USER, "Hello")

                val updatedSession = session.addMessage(message)

                (updatedSession.updatedAt >= originalUpdatedAt) shouldBe true
            }

            "should add multiple messages" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

                val sessionWithMsg1 = session.addMessage(msg1)
                val sessionWithMsg2 = sessionWithMsg1.addMessage(msg2)

                sessionWithMsg2.messages.size shouldBe 2
                sessionWithMsg2.messages[0] shouldBe msg1
                sessionWithMsg2.messages[1] shouldBe msg2
            }
        }

        "ClearMessages" - {

            "should clear all messages" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
                val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

                val clearedSession = sessionWithMessages.clearMessages()

                clearedSession.messages.isEmpty() shouldBe true
                clearedSession.isActive() shouldBe false
            }

            "should update updatedAt when clearing" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val message = Message.create(session.id, MessageRole.USER, "Hello")
                val sessionWithMessage = session.addMessage(message)
                val originalUpdatedAt = sessionWithMessage.updatedAt

                val clearedSession = sessionWithMessage.clearMessages()

                (clearedSession.updatedAt >= originalUpdatedAt) shouldBe true
            }
        }

        "GetRecentMessages" - {

            "should return last N messages" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
                val msg3 = Message.create(session.id, MessageRole.USER, "Third")
                val sessionWithMessages = session.addMessage(msg1).addMessage(msg2).addMessage(msg3)

                val recent = sessionWithMessages.getRecentMessages(2)

                recent.size shouldBe 2
                recent[0] shouldBe msg2
                recent[1] shouldBe msg3
            }

            "should return all messages when limit is greater than count" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)
                val msg1 = Message.create(session.id, MessageRole.USER, "First")
                val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
                val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

                val recent = sessionWithMessages.getRecentMessages(10)

                recent.size shouldBe 2
            }

            "should return empty list when no messages" {
                val session = DialogSession.create(SessionLevel.TASK_LIST)

                val recent = session.getRecentMessages(5)

                recent.isEmpty() shouldBe true
            }
        }
    }
})
