package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSource
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.*

/**
 * Тесты для sealed interface [ChatMessage] и [ChatSource].
 */
class ChatMessageTest : FreeSpec({

    "User message" - {

        "should create with valid fields" {
            // given
            val id = UUID.randomUUID()
            val sessionId = UUID.randomUUID()
            val now = Instant.now()

            // when
            val msg = ChatMessage.User(
                id = id,
                sessionId = sessionId,
                text = "Hello, world!",
                createdAt = now
            )

            // then
            msg.id shouldBe id
            msg.sessionId shouldBe sessionId
            msg.text shouldBe "Hello, world!"
            msg.createdAt shouldBe now
            (msg is ChatMessage.User) shouldBe true
        }
    }

    "Assistant message" - {

        "should create with citations and sources" {
            // given
            val id = UUID.randomUUID()
            val sessionId = UUID.randomUUID()
            val now = Instant.now()
            val sources = listOf(
                ChatSource(
                    citationNumber = 1,
                    documentId = "doc-1",
                    documentName = "Doc One",
                    relevance = 0.95f
                )
            )

            // when
            val msg = ChatMessage.Assistant(
                id = id,
                sessionId = sessionId,
                text = "Here is the answer",
                citations = listOf(1),
                sources = sources,
                createdAt = now
            )

            // then
            msg.id shouldBe id
            msg.sessionId shouldBe sessionId
            msg.text shouldBe "Here is the answer"
            msg.citations shouldBe listOf(1)
            msg.sources.size shouldBe 1
            msg.sources[0].documentId shouldBe "doc-1"
            (msg is ChatMessage.Assistant) shouldBe true
        }

        "should create with empty citations and sources" {
            // given
            val msg = ChatMessage.Assistant(
                id = UUID.randomUUID(),
                sessionId = UUID.randomUUID(),
                text = "Answer without sources",
                citations = emptyList(),
                sources = emptyList(),
                createdAt = Instant.now()
            )

            // then
            msg.citations.isEmpty() shouldBe true
            msg.sources.isEmpty() shouldBe true
        }
    }

    "System message" - {

        "should create with valid fields" {
            // given
            val id = UUID.randomUUID()
            val sessionId = UUID.randomUUID()
            val now = Instant.now()

            // when
            val msg = ChatMessage.System(
                id = id,
                sessionId = sessionId,
                text = "System notification",
                createdAt = now
            )

            // then
            msg.id shouldBe id
            msg.sessionId shouldBe sessionId
            msg.text shouldBe "System notification"
            msg.createdAt shouldBe now
            (msg is ChatMessage.System) shouldBe true
        }
    }

    "ChatSource" - {

        "should create with valid fields" {
            // when
            val source = ChatSource(
                citationNumber = 3,
                documentId = "doc-xyz",
                documentName = "Important Doc",
                relevance = 0.88f
            )

            // then
            source.citationNumber shouldBe 3
            source.documentId shouldBe "doc-xyz"
            source.documentName shouldBe "Important Doc"
            source.relevance shouldBe 0.88f
        }
    }

    "Sealed interface — type check" - {

        "should distinguish message types" {
            val now = Instant.now()
            val sid = UUID.randomUUID()

            val user = ChatMessage.User(UUID.randomUUID(), sid, "text", now)
            val assistant = ChatMessage.Assistant(UUID.randomUUID(), sid, "text", emptyList(), emptyList(), now)
            val system = ChatMessage.System(UUID.randomUUID(), sid, "text", now)

            (user is ChatMessage.User) shouldBe true
            (assistant is ChatMessage.Assistant) shouldBe true
            (system is ChatMessage.System) shouldBe true
        }
    }
})
