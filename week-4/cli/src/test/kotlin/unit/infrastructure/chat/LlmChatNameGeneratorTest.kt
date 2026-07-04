package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.chat.LlmChatNameGenerator
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.*

/**
 * Тесты для [LlmChatNameGenerator] — LLM-генерация имени чата.
 *
 * Мокаем [LlmPort], проверяем санитизацию имени.
 */
class LlmChatNameGeneratorTest : FreeSpec({

    fun createGenerator(llmPort: LlmPort) = LlmChatNameGenerator(llmPort)

    fun createMessages(sessionId: UUID): List<ChatMessage> = listOf(
        ChatMessage.User(
            id = UUID.randomUUID(),
            sessionId = sessionId,
            text = "Tell me about REST APIs",
            createdAt = Instant.now()
        ),
        ChatMessage.Assistant(
            id = UUID.randomUUID(),
            sessionId = sessionId,
            text = "REST APIs are...",
            citations = emptyList(),
            sources = emptyList(),
            createdAt = Instant.now()
        )
    )

    "Valid response" - {

        "should return trimmed name" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success("REST API Discussion")
                val generator = createGenerator(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = generator.generate(createMessages(sessionId))

                // then
                result.isSuccess shouldBe true
                result.getOrNull() shouldBe "REST API Discussion"
            }
        }

        "should strip surrounding quotes" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success("\"My Chat Name\"")
                val generator = createGenerator(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = generator.generate(createMessages(sessionId))

                // then
                result.isSuccess shouldBe true
                result.getOrNull() shouldBe "My Chat Name"
            }
        }

        "should truncate name exceeding 50 characters" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                val longName = "A".repeat(60)
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success(longName)
                val generator = createGenerator(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = generator.generate(createMessages(sessionId))

                // then
                result.isSuccess shouldBe true
                val name = result.getOrNull()!!
                name.length shouldBe 50
            }
        }
    }

    "Empty/blank response" - {

        "should return 'New Chat' for empty string" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success("")
                val generator = createGenerator(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = generator.generate(createMessages(sessionId))

                // then
                result.isSuccess shouldBe true
                result.getOrNull() shouldBe "New Chat"
            }
        }

        "should return 'New Chat' for blank string" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success("   ")
                val generator = createGenerator(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = generator.generate(createMessages(sessionId))

                // then
                result.isSuccess shouldBe true
                result.getOrNull() shouldBe "New Chat"
            }
        }
    }

    "Error handling" - {

        "should return failure when LLM returns Error" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Error("LLM error")
                val generator = createGenerator(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = generator.generate(createMessages(sessionId))

                // then
                result.isFailure shouldBe true
            }
        }

        "should return failure when LLM throws exception" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } throws
                        RuntimeException("Network error")
                val generator = createGenerator(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = generator.generate(createMessages(sessionId))

                // then
                result.isFailure shouldBe true
            }
        }
    }
})
