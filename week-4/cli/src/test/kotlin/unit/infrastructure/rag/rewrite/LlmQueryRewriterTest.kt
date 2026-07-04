package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.rag.rewrite

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rewrite.LlmQueryRewriter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class LlmQueryRewriterTest : FreeSpec({

    lateinit var llmPort: LlmPort
    lateinit var rewriter: LlmQueryRewriter

    beforeEach {
        llmPort = mockk()
        rewriter = LlmQueryRewriter(llmPort)
    }

    "rewrite" - {

        "successfully rewrites query" {
            runTest {
                // given
                val originalQuery = "What is Kotlin?"
                val rewritten = "Define Kotlin programming language features and history"
                coEvery { llmPort.chat(any<Prompt>(), any()) } returns TaskResult.Success(rewritten)

                // when
                val result = rewriter.rewrite(originalQuery)

                // then
                result.rewrittenQuery shouldBe rewritten
                (result.tokenUsage > 0) shouldBe true
            }
        }

        "falls back to original query on LLM error" {
            runTest {
                // given
                val originalQuery = "What is Kotlin?"
                coEvery { llmPort.chat(any<Prompt>(), any()) } throws RuntimeException("API error")

                // when
                val result = rewriter.rewrite(originalQuery)

                // then
                result.rewrittenQuery shouldBe originalQuery
                result.tokenUsage shouldBe 0
            }
        }

        "trims whitespace from rewritten query" {
            runTest {
                // given
                coEvery { llmPort.chat(any<Prompt>(), any()) } returns TaskResult.Success("  trimmed query  ")

                // when
                val result = rewriter.rewrite("original")

                // then
                result.rewrittenQuery shouldBe "trimmed query"
            }
        }
    }
})
