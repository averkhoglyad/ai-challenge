package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.*

/**
 * Тесты для доменной модели [ChatSession].
 */
class ChatSessionTest : FreeSpec({

    "Создание сессии" - {

        "should create session with default values" {
            // when
            val session = ChatSession.create()

            // then
            session.metadata.name shouldBe "New Chat"
            session.metadata.active shouldBe true
            session.metadata.archived shouldBe false
            session.metadata.nameGenerated shouldBe false
            session.messages.isEmpty() shouldBe true
            session.taskState shouldBe TaskState.EMPTY
            session.config shouldBe ChatConfig()
        }

        "should create session with custom name" {
            // when
            val session = ChatSession.create(name = "My Chat")

            // then
            session.metadata.name shouldBe "My Chat"
        }

        "should create session with custom config" {
            // given
            val config = ChatConfig(historyWindowSize = 10)

            // when
            val session = ChatSession.create(config = config)

            // then
            session.config.historyWindowSize shouldBe 10
        }
    }

    "addMessage" - {

        "should add message and return new copy" {
            // given
            val session = ChatSession.create()
            val msg = ChatMessage.User(
                id = UUID.randomUUID(),
                sessionId = session.metadata.id,
                text = "Hello",
                createdAt = Instant.now()
            )

            // when
            val updated = session.addMessage(msg)

            // then
            updated.messages.size shouldBe 1
            updated.messages[0] shouldBe msg
            // Исходная сессия не изменилась (immutable)
            session.messages.isEmpty() shouldBe true
        }

        "should update updatedAt when adding message" {
            // given
            val session = ChatSession.create()
            val originalUpdatedAt = session.metadata.updatedAt
            val msg = ChatMessage.User(
                id = UUID.randomUUID(),
                sessionId = session.metadata.id,
                text = "Hello",
                createdAt = Instant.now()
            )

            // when
            val updated = session.addMessage(msg)

            // then
            (updated.metadata.updatedAt >= originalUpdatedAt) shouldBe true
        }

        "should throw when message sessionId doesn't match" {
            // given
            val session = ChatSession.create()
            val msg = ChatMessage.User(
                id = UUID.randomUUID(),
                sessionId = UUID.randomUUID(),
                text = "Hello",
                createdAt = Instant.now()
            )

            // when & then
            shouldThrow<IllegalArgumentException> {
                session.addMessage(msg)
            }
        }

        "should add multiple messages preserving order" {
            // given
            val session = ChatSession.create()
            val msg1 = ChatMessage.User(
                id = UUID.randomUUID(), sessionId = session.metadata.id,
                text = "First", createdAt = Instant.now()
            )
            val msg2 = ChatMessage.Assistant(
                id = UUID.randomUUID(), sessionId = session.metadata.id,
                text = "Second", citations = emptyList(), sources = emptyList(),
                createdAt = Instant.now()
            )

            // when
            val updated = session.addMessage(msg1).addMessage(msg2)

            // then
            updated.messages.size shouldBe 2
            updated.messages[0] shouldBe msg1
            updated.messages[1] shouldBe msg2
        }
    }

    "archive / activate" - {

        "should archive session and set active=false" {
            // given
            val session = ChatSession.create()

            // when
            val archived = session.archive()

            // then
            archived.metadata.archived shouldBe true
            archived.metadata.active shouldBe false
        }

        "should activate session" {
            // given
            val session = ChatSession.create().archive()

            // when
            val activated = session.activate()

            // then
            activated.metadata.active shouldBe true
        }

        "activate should not change archived flag" {
            // given
            val session = ChatSession.create()
            // Activate an already-active, non-archived session
            val activated = session.activate()

            // then
            activated.metadata.active shouldBe true
            activated.metadata.archived shouldBe false
        }
    }

    "rename" - {

        "should change name" {
            // given
            val session = ChatSession.create()

            // when
            val renamed = session.rename("New Name", generated = true)

            // then
            renamed.metadata.name shouldBe "New Name"
            renamed.metadata.nameGenerated shouldBe true
        }

        "should update updatedAt" {
            // given
            val session = ChatSession.create()
            val originalUpdatedAt = session.metadata.updatedAt

            // when
            val renamed = session.rename("New Name")

            // then
            (renamed.metadata.updatedAt >= originalUpdatedAt) shouldBe true
        }

        "should throw when name is blank" {
            // given
            val session = ChatSession.create()

            // when & then
            shouldThrow<IllegalArgumentException> {
                session.rename("  ")
            }
        }

        "should throw when name exceeds max length" {
            // given
            val config = ChatConfig(nameMaxLength = 10)
            val session = ChatSession.create(config = config)

            // when & then
            shouldThrow<IllegalArgumentException> {
                session.rename("This name is way too long")
            }
        }
    }

    "getRecentMessages" - {

        "should return last N messages" {
            // given
            val session = ChatSession.create()
            val msgs = (1..5).map { i ->
                ChatMessage.User(
                    id = UUID.randomUUID(), sessionId = session.metadata.id,
                    text = "Message $i", createdAt = Instant.now()
                )
            }
            val filled = msgs.fold(session) { s, m -> s.addMessage(m) }

            // when
            val recent = filled.getRecentMessages(3)

            // then
            recent.size shouldBe 3
            recent[0].let { it as ChatMessage.User }.text shouldBe "Message 3"
            recent[2].let { it as ChatMessage.User }.text shouldBe "Message 5"
        }

        "should return all messages when limit exceeds count" {
            // given
            val session = ChatSession.create()
            val msg = ChatMessage.User(
                id = UUID.randomUUID(), sessionId = session.metadata.id,
                text = "Only one", createdAt = Instant.now()
            )
            val filled = session.addMessage(msg)

            // when
            val recent = filled.getRecentMessages(10)

            // then
            recent.size shouldBe 1
        }

        "should return empty list when no messages" {
            // given
            val session = ChatSession.create()

            // when
            val recent = session.getRecentMessages(5)

            // then
            recent.isEmpty() shouldBe true
        }

        "should use config.historyWindowSize as default limit" {
            // given
            val config = ChatConfig(historyWindowSize = 3)
            val session = ChatSession.create(config = config)
            val msgs = (1..6).map { i ->
                ChatMessage.User(
                    id = UUID.randomUUID(), sessionId = session.metadata.id,
                    text = "Message $i", createdAt = Instant.now()
                )
            }
            val filled = msgs.fold(session) { s, m -> s.addMessage(m) }

            // when
            val recent = filled.getRecentMessages()

            // then
            recent.size shouldBe 3
        }
    }

    "isActive" - {

        "should return true for active non-archived session" {
            // given
            val session = ChatSession.create()

            // then
            session.isActive() shouldBe true
        }

        "should return false for archived session" {
            // given
            val session = ChatSession.create().archive()

            // then
            session.isActive() shouldBe false
        }
    }

    "updateTaskState" - {

        "should update task state and updatedAt" {
            // given
            val session = ChatSession.create()
            val newState = TaskState(goal = "New Goal")
            val originalUpdatedAt = session.metadata.updatedAt

            // when
            val updated = session.updateTaskState(newState)

            // then
            updated.taskState.goal shouldBe "New Goal"
            (updated.metadata.updatedAt >= originalUpdatedAt) shouldBe true
        }
    }
})
