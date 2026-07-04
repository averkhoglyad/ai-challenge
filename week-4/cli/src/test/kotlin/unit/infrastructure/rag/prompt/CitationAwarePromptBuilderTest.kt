package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.rag.prompt

import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.prompt.CitationAwarePromptBuilder
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.util.*

class CitationAwarePromptBuilderTest : FreeSpec({

    val config = RagConfig(maxCitationsPerAnswer = 3, minCitationsRequired = 1)
    val builder = CitationAwarePromptBuilder(config)
    val runId = UUID.randomUUID()

    fun chunk(text: String, source: String = "docs/test.md", id: UUID = UUID.randomUUID()): RelevantChunk {
        val c = Chunk(
            id = id,
            runId = runId,
            contentHash = "hash-${text.hashCode()}",
            source = source,
            title = "test.md",
            section = null,
            text = text,
            strategy = ChunkingStrategyType.FIXED_SIZE,
            metadata = emptyMap()
        )
        return RelevantChunk(c, 0.9f)
    }

    "build" - {

        "returns question as-is when context is empty" {
            val result = builder.build("What is Kotlin?", emptyList())
            result shouldBe "What is Kotlin?"
        }

        "includes numbered citations [1], [2]" {
            val context = listOf(
                chunk("Kotlin is a programming language"),
                chunk("Kotlin runs on the JVM")
            )
            val result = builder.build("What is Kotlin?", context)
            result shouldContain "[1] Kotlin is a programming language"
            result shouldContain "[2] Kotlin runs on the JVM"
        }

        "limits citations to maxCitationsPerAnswer" {
            val context = (1..10).map { i ->
                chunk("Chunk text $i", id = UUID.randomUUID())
            }
            val result = builder.build("question", context)
            result shouldContain "[3]"
            result shouldNotContain "[4]"
        }

        "includes anti-hallucination instructions" {
            val context = listOf(chunk("Some text"))
            val result = builder.build("question", context)
            result shouldContain "ТОЛЬКО информацию из цитат"
            result shouldContain "НЕ придумывай информацию"
            result shouldContain "НЕ используй свои общие знания"
        }

        "includes INSUFFICIENT_CONTEXT instruction" {
            val context = listOf(chunk("Some text"))
            val result = builder.build("question", context)
            result shouldContain "INSUFFICIENT_CONTEXT"
            result shouldContain "Я не могу ответить"
        }

        "requires JSON format response" {
            val context = listOf(chunk("Some text"))
            val result = builder.build("question", context)
            result shouldContain "JSON-формате"
            result shouldContain "\"answer\""
            result shouldContain "\"citations_used\""
        }

        "includes original question in prompt" {
            val context = listOf(chunk("Some text"))
            val result = builder.build("What is the capital of France?", context)
            result shouldContain "What is the capital of France?"
        }

        "includes source info for each citation" {
            val context = listOf(chunk("Chunk content", "geography.md"))
            val result = builder.build("question", context)
            result shouldContain "Источник: geography.md"
        }
    }
})
