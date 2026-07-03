package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Message
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MessageRole
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Тесты для доменной модели [Message].
 */
class MessageTest : FreeSpec({

    "creation" - {

        "should create with valid data" {
            // given
            val sessionId = SessionId("test-session")

            // when
            val message = Message(
                id = "msg-1",
                sessionId = sessionId,
                role = MessageRole.USER,
                content = "Hello",
                timestamp = java.time.Instant.now()
            )

            // then
            message.id shouldBe "msg-1"
            message.sessionId shouldBe sessionId
            message.role shouldBe MessageRole.USER
            message.content shouldBe "Hello"
        }
    }

    "validation" - {

        "should throw when id is blank" {
            // given
            val sessionId = SessionId("test-session")

            // when & then
            shouldThrow<IllegalArgumentException> {
                Message(
                    id = "",
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = "Hello",
                    timestamp = java.time.Instant.now()
                )
            }
        }

        "should throw when content is blank" {
            // given
            val sessionId = SessionId("test-session")

            // when & then
            shouldThrow<IllegalArgumentException> {
                Message(
                    id = "msg-1",
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = "",
                    timestamp = java.time.Instant.now()
                )
            }
        }
    }

    "factory method create()" - {

        "should create with auto-generated id" {
            // given
            val sessionId = SessionId("test-session")

            // when
            val message = Message.create(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "Hi there"
            )

            // then
            message.id.isNotBlank() shouldBe true
            message.sessionId shouldBe sessionId
            message.role shouldBe MessageRole.ASSISTANT
            message.content shouldBe "Hi there"
        }

        "should create messages with different roles" {
            // given
            val sessionId = SessionId("test-session")

            // when
            val systemMsg = Message.create(sessionId, MessageRole.SYSTEM, "System message")
            val userMsg = Message.create(sessionId, MessageRole.USER, "User message")
            val assistantMsg = Message.create(sessionId, MessageRole.ASSISTANT, "Assistant message")

            // then
            systemMsg.role shouldBe MessageRole.SYSTEM
            userMsg.role shouldBe MessageRole.USER
            assistantMsg.role shouldBe MessageRole.ASSISTANT
        }

        "should generate unique ids" {
            // given
            val sessionId = SessionId("test-session")

            // when
            val msg1 = Message.create(sessionId, MessageRole.USER, "First")
            val msg2 = Message.create(sessionId, MessageRole.USER, "Second")

            // then
            (msg1.id != msg2.id) shouldBe true
        }
    }
})
