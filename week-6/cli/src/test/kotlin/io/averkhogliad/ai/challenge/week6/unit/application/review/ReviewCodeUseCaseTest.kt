package io.averkhogliad.ai.challenge.week6.unit.application.review

import io.averkhogliad.ai.challenge.llm.chat.*
import io.averkhogliad.ai.challenge.week6.application.review.ReviewCodeUseCase
import io.averkhogliad.ai.challenge.week6.application.review.SaveReviewUseCase
import io.averkhogliad.ai.challenge.week6.domain.review.Review
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewRepository
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewTrigger
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ReviewCodeUseCaseTest : FreeSpec({

    // Fake LLM client that returns pre-configured responses
    class FakeLlmClient(
        var chatResponse: ChatResponse = ChatResponse(null, null, null),
        var chatWithMessagesResponse: ChatResponse = ChatResponse(null, null, null),
        var shouldThrow: Boolean = false,
        var errorMessage: String = "LLM error",
    ) : LlmClient {
        override suspend fun chat(
            prompt: String,
            systemPrompt: String?,
            parameters: ChatParameters,
            model: String?,
            tools: List<JsonObject>?
        ): ChatResponse {
            if (shouldThrow) throw RuntimeException(errorMessage)
            return chatResponse
        }

        override suspend fun chatWithMessages(
            messages: List<ChatMessage>,
            parameters: ChatParameters,
            model: String?,
            tools: List<JsonObject>?
        ): ChatResponse {
            if (shouldThrow) throw RuntimeException(errorMessage)
            return chatWithMessagesResponse
        }

        override fun close() {}
    }

    val testDiff = """
        diff --git a/src/main/App.kt b/src/main/App.kt
        +fun newFunction() {
        +    val x = null
        +    x.toString()
        +}
    """.trimIndent()

    "execute" - {

        "handles tool-calling mode and saves review" {
            runTest {
                // given — review repository mock + SaveReviewUseCase
                val reviewRepository = mockk<ReviewRepository>()
                coEvery { reviewRepository.save(any()) } returns Unit
                val saveReviewUseCase = SaveReviewUseCase(reviewRepository)

                val findingsJson = buildJsonObject {
                    put("summary", "Found null safety issues")
                    put("findings", buildJsonArray {
                        add(buildJsonObject {
                            put("category", "BUG")
                            put("severity", "CRITICAL")
                            put("file", "src/main/App.kt")
                            put("line", 3)
                            put("description", "Null pointer dereference")
                            put("recommendation", "Use safe call operator")
                        })
                    })
                }.toString()

                val toolCall = ToolCall(
                    id = "call_1",
                    function = FunctionCall("save_review", findingsJson)
                )
                val toolResponse = ChatResponse(
                    content = null,
                    finishReason = "tool_calls",
                    usage = null,
                    toolCalls = listOf(toolCall),
                )
                val finalResponse = ChatResponse(
                    content = "Review summary: one critical issue found",
                    finishReason = "stop",
                    usage = null,
                )

                val fakeLlm = FakeLlmClient(
                    chatResponse = toolResponse,
                    chatWithMessagesResponse = finalResponse,
                )
                val useCase = ReviewCodeUseCase(fakeLlm, null, saveReviewUseCase)

                // when
                val results = useCase.execute(
                    projectId = "test-1",
                    diff = testDiff,
                    trigger = ReviewTrigger.MANUAL,
                ).toList()

                // then
                val fullOutput = results.joinToString("")
                fullOutput shouldContain "Review completed"
                fullOutput shouldContain "Review ID:"
                fullOutput shouldNotContain "\u274C"

                coVerify(exactly = 1) { reviewRepository.save(any()) }
            }
        }

        "handles JSON fallback mode and saves review" {
            runTest {
                // given
                val reviewRepository = mockk<ReviewRepository>()
                val reviewSlot = slot<Review>()
                coEvery { reviewRepository.save(capture(reviewSlot)) } returns Unit
                val saveReviewUseCase = SaveReviewUseCase(reviewRepository)

                val jsonResponse = """
                    {
                        "findings": [
                            {
                                "category": "PERFORMANCE",
                                "severity": "WARNING",
                                "file": "src/main/App.kt",
                                "line": 1,
                                "description": "Unnecessary object creation",
                                "recommendation": "Use lazy initialization"
                            },
                            {
                                "category": "BEST_PRACTICE",
                                "severity": "INFO",
                                "description": "Consider adding documentation"
                            }
                        ],
                        "summary": "Two minor issues found"
                    }
                """.trimIndent()

                val chatResponse = ChatResponse(
                    content = jsonResponse,
                    finishReason = "stop",
                    usage = null,
                    toolCalls = null,
                )

                val fakeLlm = FakeLlmClient(chatResponse = chatResponse)
                val useCase = ReviewCodeUseCase(fakeLlm, null, saveReviewUseCase)

                // when
                val results = useCase.execute(
                    projectId = "test-1",
                    diff = testDiff,
                    trigger = ReviewTrigger.AUTO,
                    commitHash = "abc123",
                ).toList()

                // then
                val fullOutput = results.joinToString("")
                fullOutput shouldContain "JSON mode"
                fullOutput shouldContain "2 issue(s)"

                coVerify(exactly = 1) { reviewRepository.save(any()) }
                reviewSlot.captured.trigger shouldBe ReviewTrigger.AUTO
                reviewSlot.captured.commitHash shouldBe "abc123"
                reviewSlot.captured.findings shouldHaveSize 2
            }
        }

        "handles LLM error gracefully with no save" {
            runTest {
                // given
                val reviewRepository = mockk<ReviewRepository>()
                val saveReviewUseCase = SaveReviewUseCase(reviewRepository)

                val fakeLlm = FakeLlmClient(shouldThrow = true, errorMessage = "Connection refused")
                val useCase = ReviewCodeUseCase(fakeLlm, null, saveReviewUseCase)

                // when
                val results = useCase.execute(
                    projectId = "test-1",
                    diff = testDiff,
                    trigger = ReviewTrigger.MANUAL,
                ).toList()

                // then
                val fullOutput = results.joinToString("")
                fullOutput shouldContain "\u274C Review failed"
                fullOutput shouldContain "Connection refused"
                coVerify(exactly = 0) { reviewRepository.save(any()) }
            }
        }

        "handles empty diff with no findings saved" {
            runTest {
                // given
                val reviewRepository = mockk<ReviewRepository>()
                coEvery { reviewRepository.save(any()) } returns Unit
                val saveReviewUseCase = SaveReviewUseCase(reviewRepository)

                val chatResponse = ChatResponse(
                    content = """{"findings": [], "summary": "No changes to review"}""",
                    finishReason = "stop",
                    usage = null,
                )
                val fakeLlm = FakeLlmClient(chatResponse = chatResponse)
                val useCase = ReviewCodeUseCase(fakeLlm, null, saveReviewUseCase)

                // when
                val results = useCase.execute(
                    projectId = "test-1",
                    diff = "",
                    trigger = ReviewTrigger.AUTO,
                ).toList()

                // then
                val fullOutput = results.joinToString("")
                fullOutput shouldContain "Review ID:"

                coVerify(exactly = 1) { reviewRepository.save(any()) }
            }
        }

        "does not save when SaveReviewUseCase is null" {
            runTest {
                // given
                val chatResponse = ChatResponse(
                    content = """{"findings": [{"category":"BUG","severity":"INFO","description":"test"}]}""",
                    finishReason = "stop",
                    usage = null,
                )
                val fakeLlm = FakeLlmClient(chatResponse = chatResponse)
                val useCase = ReviewCodeUseCase(fakeLlm, null, null)

                // when
                val results = useCase.execute(
                    projectId = "test-1",
                    diff = testDiff,
                    trigger = ReviewTrigger.MANUAL,
                ).toList()

                // then — should not crash
                val fullOutput = results.joinToString("")
                fullOutput shouldContain "JSON mode"
                fullOutput shouldContain "1 issue(s)"
            }
        }
    }
})
