package io.averkhogliad.ai.challenge.week4.cli.unit.application.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RagPromptBuilder
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.*

/**
 * Тесты для [ChatPromptBuilder] — формирование промпта из 4 блоков.
 */
class ChatPromptBuilderTest : FreeSpec({

    fun createBuilder(citationBuilder: RagPromptBuilder, config: ChatConfig = ChatConfig()) =
        ChatPromptBuilder(citationBuilder, config)

    fun createUserMessage(sessionId: UUID, text: String) = ChatMessage.User(
        id = UUID.randomUUID(),
        sessionId = sessionId,
        text = text,
        createdAt = Instant.now()
    )

    fun createAssistantMessage(sessionId: UUID, text: String) = ChatMessage.Assistant(
        id = UUID.randomUUID(),
        sessionId = sessionId,
        text = text,
        citations = emptyList(),
        sources = emptyList(),
        createdAt = Instant.now()
    )

    fun createRelevantChunk(text: String, score: Float = 0.9f) = RelevantChunk(
        chunk = Chunk(
            id = UUID.randomUUID(),
            runId = UUID.randomUUID(),
            contentHash = "hash",
            source = "doc1",
            title = "Doc",
            section = null,
            text = text,
            strategy = ChunkingStrategyType.FIXED_SIZE,
            metadata = emptyMap()
        ),
        score = score
    )

    "All 4 blocks present" - {

        "should include TaskState, History, RAG Context, and Question" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String =
                    "RAG CONTEXT: Found ${context.size} chunks for: $question"
            }
            val builder = createBuilder(citationBuilder)
            val sessionId = UUID.randomUUID()
            val taskState = TaskState(goal = "Test Goal")
            val history = listOf(
                createUserMessage(sessionId, "Hello"),
                createAssistantMessage(sessionId, "Hi there!")
            )
            val ragContext = listOf(createRelevantChunk("Context text"))

            // when
            val prompt = builder.build(
                taskState = taskState,
                history = history,
                ragContext = ragContext,
                question = "What is this?"
            )

            // then
            prompt.contains("=== КОНТЕКСТ ЗАДАЧИ ===") shouldBe true
            prompt.contains("Test Goal") shouldBe true
            prompt.contains("=== ИСТОРИЯ ===") shouldBe true
            prompt.contains("User: Hello") shouldBe true
            prompt.contains("Assistant: Hi there!") shouldBe true
            prompt.contains("RAG CONTEXT") shouldBe true
            prompt.contains("ВОПРОС: What is this?") shouldBe true
        }
    }

    "TaskState formatting" - {

        "should include goal when set" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)
            val taskState = TaskState(goal = "Implement API")

            // when
            val prompt = builder.build(taskState, emptyList(), emptyList(), "Question")

            // then
            prompt.contains("Цель: Implement API") shouldBe true
        }

        "should omit goal when not set" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)
            val taskState = TaskState.EMPTY

            // when
            val prompt = builder.build(taskState, emptyList(), emptyList(), "Question")

            // then
            prompt.contains("Цель:") shouldBe false
        }

        "should include defined terms" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)
            val taskState = TaskState(
                definedTerms = listOf("API" to "Application Programming Interface")
            )

            // when
            val prompt = builder.build(taskState, emptyList(), emptyList(), "Question")

            // then
            prompt.contains("Термины:") shouldBe true
            prompt.contains("API: Application Programming Interface") shouldBe true
        }

        "should include constraints" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)
            val taskState = TaskState(constraints = listOf("Must be fast"))

            // when
            val prompt = builder.build(taskState, emptyList(), emptyList(), "Question")

            // then
            prompt.contains("Ограничения:") shouldBe true
            prompt.contains("Must be fast") shouldBe true
        }

        "should include clarified facts" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)
            val taskState = TaskState(clarifiedFacts = listOf("Fact about system"))

            // when
            val prompt = builder.build(taskState, emptyList(), emptyList(), "Question")

            // then
            prompt.contains("Уточнённые факты:") shouldBe true
            prompt.contains("Fact about system") shouldBe true
        }
    }

    "History formatting" - {

        "should show empty history for no messages" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)

            // when
            val prompt = builder.build(TaskState.EMPTY, emptyList(), emptyList(), "Question")

            // then
            prompt.contains("(пусто)") shouldBe true
        }

        "should format User and Assistant messages correctly" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)
            val sessionId = UUID.randomUUID()
            val history = listOf(
                createUserMessage(sessionId, "Question"),
                createAssistantMessage(sessionId, "Answer")
            )

            // when
            val prompt = builder.build(TaskState.EMPTY, history, emptyList(), "New Question")

            // then
            prompt.contains("User: Question") shouldBe true
            prompt.contains("Assistant: Answer") shouldBe true
        }
    }

    "RAG Context" - {

        "should skip RAG block when context is empty" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)

            // when
            val prompt = builder.build(TaskState.EMPTY, emptyList(), emptyList(), "Question")

            // then
            // RAG block should not appear (empty context, no call to citationBuilder)
            prompt.contains("RAG") shouldBe false
        }

        "should call citationPromptBuilder when ragContext is non-empty" {
            // given
            var wasCalled = false
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String {
                    wasCalled = true
                    return "RAG CONTEXT"
                }
            }
            val builder = createBuilder(citationBuilder)
            val ragContext = listOf(createRelevantChunk("text", 0.8f))

            // when
            val prompt = builder.build(TaskState.EMPTY, emptyList(), ragContext, "Question")

            // then
            wasCalled shouldBe true
            prompt.contains("RAG CONTEXT") shouldBe true
        }
    }

    "Token limit trimming" - {

        "should not trim small prompts" {
            // given
            val citationBuilder = object : RagPromptBuilder {
                override fun build(question: String, context: List<RelevantChunk>): String = ""
            }
            val builder = createBuilder(citationBuilder)

            // when
            val prompt = builder.build(TaskState.EMPTY, emptyList(), emptyList(), "Short")

            // then
            prompt.contains("промпт обрезан") shouldBe false
        }
    }
})
