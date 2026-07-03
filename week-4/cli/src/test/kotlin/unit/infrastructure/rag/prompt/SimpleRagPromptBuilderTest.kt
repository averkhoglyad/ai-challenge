package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.rag.prompt

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.prompt.SimpleRagPromptBuilder
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.util.*

class SimpleRagPromptBuilderTest : FreeSpec({

    val builder = SimpleRagPromptBuilder()
    val runId = UUID.randomUUID()

    fun chunk(text: String, source: String = "docs/test.md"): Chunk = Chunk(
        id = UUID.randomUUID(),
        runId = runId,
        contentHash = "hash-${text.hashCode()}",
        source = source,
        title = "test.md",
        section = null,
        text = text,
        strategy = ChunkingStrategyType.STRUCTURAL,
        metadata = emptyMap()
    )

    "build" - {

        "returns question as-is when context is empty" {
            val result = builder.build("Как работает авторизация?", emptyList())

            result shouldBe "Как работает авторизация?"
        }

        "includes instruction, context, question and fallback phrase" {
            val context = listOf(
                RelevantChunk(chunk("Текст первого чанка про API", "docs/api.md"), 0.9f),
                RelevantChunk(chunk("Текст второго чанка про токены", "docs/auth.md"), 0.8f)
            )

            val result = builder.build("Вопрос про авторизацию?", context)

            result shouldContain "Ответь на вопрос, основываясь на следующем контексте:"
            result shouldContain "[Источник: docs/api.md]"
            result shouldContain "[Источник: docs/auth.md]"
            result shouldContain "Текст первого чанка про API"
            result shouldContain "Текст второго чанка про токены"
            result shouldContain "Вопрос: Вопрос про авторизацию?"
            result shouldContain "У меня недостаточно информации"
        }

        "handles single chunk correctly" {
            val context = listOf(
                RelevantChunk(chunk("Единственный чанк", "docs/single.md"), 0.95f)
            )

            val result = builder.build("Вопрос?", context)

            result shouldContain "[Источник: docs/single.md]"
            result shouldContain "Единственный чанк"
            result shouldNotContain "\n\n\n" // no extra empty lines between single chunk
        }
    }
})
