package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.chat.LlmTaskStateExtractor
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.*

/**
 * Тесты для [LlmTaskStateExtractor] — LLM-извлечение дельты TaskState.
 *
 * Мокаем [LlmPort], проверяем парсинг JSON-ответа.
 */
class LlmTaskStateExtractorTest : FreeSpec({

    fun createExtractor(llmPort: LlmPort) = LlmTaskStateExtractor(llmPort)

    fun createUserMessage(sessionId: UUID, text: String) = ChatMessage.User(
        id = UUID.randomUUID(),
        sessionId = sessionId,
        text = text,
        createdAt = Instant.now()
    )

    "Valid JSON response" - {

        "should parse goal change" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                val jsonResponse =
                    """{"goalChange": "Build REST API", "newTerms": [], "removedTermNames": [], "newConstraints": [], "removedConstraints": [], "newClarifiedFacts": []}"""
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success(jsonResponse)
                val extractor = createExtractor(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = extractor.extract(
                    currentState = TaskState.EMPTY,
                    newMessages = listOf(createUserMessage(sessionId, "Build REST API"))
                )

                // then
                result.isSuccess shouldBe true
                val delta = result.getOrNull()
                delta shouldNotBe null
                (delta is TaskStateDelta.Composite) shouldBe true
                val composite = delta as TaskStateDelta.Composite
                composite.deltas.size shouldBe 1
                (composite.deltas[0] is TaskStateDelta.SetGoal) shouldBe true
                (composite.deltas[0] as TaskStateDelta.SetGoal).text shouldBe "Build REST API"
            }
        }

        "should parse new terms" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                val jsonResponse =
                    """{"goalChange": null, "newTerms": [{"name": "API", "definition": "Application Programming Interface"}], "removedTermNames": [], "newConstraints": [], "removedConstraints": [], "newClarifiedFacts": []}"""
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success(jsonResponse)
                val extractor = createExtractor(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = extractor.extract(
                    currentState = TaskState.EMPTY,
                    newMessages = listOf(createUserMessage(sessionId, "Define API"))
                )

                // then
                result.isSuccess shouldBe true
                val delta = result.getOrNull() as TaskStateDelta.Composite
                delta.deltas.size shouldBe 1
                (delta.deltas[0] is TaskStateDelta.AddTerm) shouldBe true
                val addTerm = delta.deltas[0] as TaskStateDelta.AddTerm
                addTerm.name shouldBe "API"
                addTerm.definition shouldBe "Application Programming Interface"
            }
        }

        "should parse constraints" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                val jsonResponse =
                    """{"goalChange": null, "newTerms": [], "removedTermNames": [], "newConstraints": ["Must be fast"], "removedConstraints": [], "newClarifiedFacts": []}"""
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success(jsonResponse)
                val extractor = createExtractor(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = extractor.extract(
                    currentState = TaskState.EMPTY,
                    newMessages = listOf(createUserMessage(sessionId, "Add constraint"))
                )

                // then
                result.isSuccess shouldBe true
                val delta = result.getOrNull() as TaskStateDelta.Composite
                delta.deltas.size shouldBe 1
                (delta.deltas[0] is TaskStateDelta.AddConstraint) shouldBe true
                (delta.deltas[0] as TaskStateDelta.AddConstraint).text shouldBe "Must be fast"
            }
        }
    }

    "Empty changes" - {

        "should return NoChanges when all arrays empty and goalChange is null" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                val jsonResponse =
                    """{"goalChange": null, "newTerms": [], "removedTermNames": [], "newConstraints": [], "removedConstraints": [], "newClarifiedFacts": []}"""
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success(jsonResponse)
                val extractor = createExtractor(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = extractor.extract(
                    currentState = TaskState.EMPTY,
                    newMessages = listOf(createUserMessage(sessionId, "Hello"))
                )

                // then
                result.isSuccess shouldBe true
                result.getOrNull() shouldBe TaskStateDelta.NoChanges
            }
        }
    }

    "Invalid JSON response" - {

        "should return failure on invalid JSON" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success("not valid json {{{")
                val extractor = createExtractor(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = extractor.extract(
                    currentState = TaskState.EMPTY,
                    newMessages = listOf(createUserMessage(sessionId, "Hello"))
                )

                // then
                result.isFailure shouldBe true
            }
        }
    }

    "LLM error handling" - {

        "should return failure when LLM returns Error" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Error("LLM is down")
                val extractor = createExtractor(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = extractor.extract(
                    currentState = TaskState.EMPTY,
                    newMessages = listOf(createUserMessage(sessionId, "Hello"))
                )

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
                val extractor = createExtractor(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = extractor.extract(
                    currentState = TaskState.EMPTY,
                    newMessages = listOf(createUserMessage(sessionId, "Hello"))
                )

                // then
                result.isFailure shouldBe true
            }
        }
    }

    "JSON with markdown fences" - {

        "should strip markdown code fences from response" {
            runTest {
                // given
                val llmPort = mockk<LlmPort>()
                val jsonResponse = """```json
{"goalChange": "New Goal", "newTerms": [], "removedTermNames": [], "newConstraints": [], "removedConstraints": [], "newClarifiedFacts": []}
```"""
                coEvery { llmPort.chatWithMessages(any(), any<TaskExecutionConfig>(), any()) } returns
                        TaskResult.Success(jsonResponse)
                val extractor = createExtractor(llmPort)
                val sessionId = UUID.randomUUID()

                // when
                val result = extractor.extract(
                    currentState = TaskState.EMPTY,
                    newMessages = listOf(createUserMessage(sessionId, "New goal"))
                )

                // then
                result.isSuccess shouldBe true
                val delta = result.getOrNull() as TaskStateDelta.Composite
                delta.deltas.size shouldBe 1
                (delta.deltas[0] as TaskStateDelta.SetGoal).text shouldBe "New Goal"
            }
        }
    }
})
