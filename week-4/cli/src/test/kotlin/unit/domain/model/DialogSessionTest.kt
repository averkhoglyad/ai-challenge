package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Тесты для доменной модели [DialogSession].
 */
class DialogSessionTest : FreeSpec({

    "Создание сессии" - {

        "should create with TASK_LIST level" {
            // when
            val session = DialogSession.create(SessionLevel.TASK_LIST)

            // then
            session.level shouldBe SessionLevel.TASK_LIST
            session.taskId shouldBe null
            session.messages.isEmpty() shouldBe true
            session.id.value.isNotBlank() shouldBe true
        }

        "should create with TASK_DETAIL level and taskId" {
            // given
            val taskId = TaskId("task-1")

            // when
            val session = DialogSession.create(SessionLevel.TASK_DETAIL, taskId)

            // then
            session.level shouldBe SessionLevel.TASK_DETAIL
            session.taskId shouldBe taskId
            session.messages.isEmpty() shouldBe true
        }

        "should throw when TASK_DETAIL without taskId" {
            // when & then
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

    "Активность сессии" - {

        "should be inactive when no messages" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)

            // when
            val result = session.isActive()

            // then
            result shouldBe false
        }

        "should be active when has messages" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")
            val sessionWithMessage = session.addMessage(message)

            // when
            val result = sessionWithMessage.isActive()

            // then
            result shouldBe true
        }
    }

    "Добавление сообщений" - {

        "should add message to session" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")

            // when
            val updatedSession = session.addMessage(message)

            // then
            updatedSession.messages.size shouldBe 1
            updatedSession.messages[0] shouldBe message
        }

        "should throw when message sessionId doesn't match" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val otherSessionId = SessionId.generate()
            val message = Message.create(otherSessionId, MessageRole.USER, "Hello")

            // when & then
            shouldThrow<IllegalArgumentException> {
                session.addMessage(message)
            }
        }

        "should update updatedAt when adding message" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val originalUpdatedAt = session.updatedAt
            val message = Message.create(session.id, MessageRole.USER, "Hello")

            // when
            val updatedSession = session.addMessage(message)

            // then
            (updatedSession.updatedAt >= originalUpdatedAt) shouldBe true
        }

        "should add multiple messages" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")

            // when
            val sessionWithMsg1 = session.addMessage(msg1)
            val sessionWithMsg2 = sessionWithMsg1.addMessage(msg2)

            // then
            sessionWithMsg2.messages.size shouldBe 2
            sessionWithMsg2.messages[0] shouldBe msg1
            sessionWithMsg2.messages[1] shouldBe msg2
        }
    }

    "Очистка сообщений" - {

        "should clear all messages" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

            // when
            val clearedSession = sessionWithMessages.clearMessages()

            // then
            clearedSession.messages.isEmpty() shouldBe true
            clearedSession.isActive() shouldBe false
        }

        "should update updatedAt when clearing" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val message = Message.create(session.id, MessageRole.USER, "Hello")
            val sessionWithMessage = session.addMessage(message)
            val originalUpdatedAt = sessionWithMessage.updatedAt

            // when
            val clearedSession = sessionWithMessage.clearMessages()

            // then
            (clearedSession.updatedAt >= originalUpdatedAt) shouldBe true
        }
    }

    "Получение последних сообщений" - {

        "should return last N messages" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val msg3 = Message.create(session.id, MessageRole.USER, "Third")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2).addMessage(msg3)

            // when
            val recent = sessionWithMessages.getRecentMessages(2)

            // then
            recent.size shouldBe 2
            recent[0] shouldBe msg2
            recent[1] shouldBe msg3
        }

        "should return all messages when limit is greater than count" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)
            val msg1 = Message.create(session.id, MessageRole.USER, "First")
            val msg2 = Message.create(session.id, MessageRole.ASSISTANT, "Second")
            val sessionWithMessages = session.addMessage(msg1).addMessage(msg2)

            // when
            val recent = sessionWithMessages.getRecentMessages(10)

            // then
            recent.size shouldBe 2
        }

        "should return empty list when no messages" {
            // given
            val session = DialogSession.create(SessionLevel.TASK_LIST)

            // when
            val recent = session.getRecentMessages(5)

            // then
            recent.isEmpty() shouldBe true
        }
    }
})
