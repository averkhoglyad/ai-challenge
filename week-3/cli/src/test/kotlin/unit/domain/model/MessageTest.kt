package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Message
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MessageRole
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class MessageTest : FreeSpec({

    "Message" - {

        "should create message with valid data" {
            val sessionId = SessionId("test-session")
            val message = Message(
                id = "msg-1",
                sessionId = sessionId,
                role = MessageRole.USER,
                content = "Hello",
                timestamp = Instant.now()
            )

            message.id shouldBe "msg-1"
            message.sessionId shouldBe sessionId
            message.role shouldBe MessageRole.USER
            message.content shouldBe "Hello"
        }

        "should throw exception when id is blank" {
            val sessionId = SessionId("test-session")
            shouldThrow<IllegalArgumentException> {
                Message(
                    id = "",
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = "Hello",
                    timestamp = Instant.now()
                )
            }
        }

        "should throw exception when content is blank" {
            val sessionId = SessionId("test-session")
            shouldThrow<IllegalArgumentException> {
                Message(
                    id = "msg-1",
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = "",
                    timestamp = Instant.now()
                )
            }
        }

        "should create message with auto-generated id" {
            val sessionId = SessionId("test-session")
            val message = Message.create(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "Hi there"
            )

            message.id.isNotBlank() shouldBe true
            message.sessionId shouldBe sessionId
            message.role shouldBe MessageRole.ASSISTANT
            message.content shouldBe "Hi there"
        }

        "should create messages with different roles" {
            val sessionId = SessionId("test-session")

            val systemMsg = Message.create(sessionId, MessageRole.SYSTEM, "System message")
            val userMsg = Message.create(sessionId, MessageRole.USER, "User message")
            val assistantMsg = Message.create(sessionId, MessageRole.ASSISTANT, "Assistant message")

            systemMsg.role shouldBe MessageRole.SYSTEM
            userMsg.role shouldBe MessageRole.USER
            assistantMsg.role shouldBe MessageRole.ASSISTANT
        }

        "should generate unique ids for different messages" {
            val sessionId = SessionId("test-session")
            val msg1 = Message.create(sessionId, MessageRole.USER, "First")
            val msg2 = Message.create(sessionId, MessageRole.USER, "Second")

            (msg1.id != msg2.id) shouldBe true
        }
    }
})
