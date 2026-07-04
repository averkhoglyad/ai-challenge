package io.averkhogliad.ai.challenge.week4.cli.it.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatExecutor
import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager
import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagQueryProcessor
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagAnswer
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RagPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatNameGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.chat.LlmTaskStateExtractor
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteChatSessionRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

/**
 * Интеграционный тест для цепочки ChatExecutor → SQLite.
 *
 * Собирает полную цепочку с in-memory SQLite:
 * - SqliteChatSessionRepository + SqliteDatabase("jdbc:sqlite::memory:")
 * - LlmTaskStateExtractor с мокнутым LlmPort
 * - ChatPromptBuilder с мокнутым RagPromptBuilder
 * - Реальные ChatExecutor + ChatSessionManager
 *
 * Сценарии:
 * - Полный цикл: сообщение → ответ
 * - Сообщения персистентны
 * - Graceful degradation (extractor fail → ответ всё равно генерируется)
 */
class ChatExecutorIT : FreeSpec({

    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteChatSessionRepository
    lateinit var chatPromptBuilder: ChatPromptBuilder
    lateinit var taskStateExtractor: LlmTaskStateExtractor
    lateinit var chatSessionManager: ChatSessionManager
    lateinit var chatExecutor: ChatExecutor

    // Mocks
    lateinit var llmPort: LlmPort
    lateinit var ragPromptBuilder: RagPromptBuilder
    lateinit var ragQueryProcessor: RagQueryProcessor
    lateinit var chatNameGenerator: ChatNameGenerator

    val config = ChatConfig(taskStateExtractionEnabled = true, autoNameEnabled = false)

    beforeSpec {
        // Подгружаем драйвер SQLite
        Class.forName("org.sqlite.JDBC")
    }

    beforeEach {
        llmPort = mockk(relaxed = true)
        ragPromptBuilder = mockk(relaxed = true)
        ragQueryProcessor = mockk(relaxed = true)
        chatNameGenerator = mockk()

        coEvery { chatNameGenerator.generate(any()) } returns Result.success("Auto Name")

        database = SqliteDatabase(":memory:")
        repository = SqliteChatSessionRepository(database)

        taskStateExtractor = LlmTaskStateExtractor(llmPort)
        chatPromptBuilder = ChatPromptBuilder(ragPromptBuilder, config)
        chatSessionManager = ChatSessionManager(repository, chatNameGenerator, config)

        chatExecutor = ChatExecutor(
            taskStateExtractor = taskStateExtractor,
            ragQueryProcessor = ragQueryProcessor,
            chatSessionRepository = repository,
            chatSessionManager = chatSessionManager,
            chatPromptBuilder = chatPromptBuilder,
            chatNameGenerator = chatNameGenerator,
            config = config
        )
    }

    afterEach {
        database.close()
    }

    // ═══════════════════════════════════════════════════════════════
    // Full cycle: message → answer
    // ═══════════════════════════════════════════════════════════════

    "Full cycle: message → answer" - {
        "should process user input and return answer" {
            runTest {
                // given
                val session = chatSessionManager.createSession("Integration Test")
                val sessionId = session.metadata.id

                // Mock LLM — extractor returns no changes
                coEvery {
                    llmPort.chatWithMessages(
                        any(),
                        any(),
                        any()
                    )
                } returns io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Success(
                    """{"goalChange":null,"newTerms":[],"removedTermNames":[],"newConstraints":[],"removedConstraints":[],"newClarifiedFacts":[]}"""
                )

                // Mock RAG — returns answer
                val ragAnswer = RagAnswer(
                    answer = "This is a test response.",
                    ragEnabled = false,
                    fallbackToPlain = true
                )
                coEvery { ragQueryProcessor.process(any(), any(), any()) } returns ragAnswer

                // Mock prompt builder
                every { ragPromptBuilder.build(any(), any()) } returns "RAG context"

                // when
                val result = chatExecutor.execute(
                    userInput = "Hello, world!",
                    sessionId = sessionId,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "This is a test response."
                result.session.messages.size shouldBe 2 // user + assistant
                result.saveSucceeded shouldBe true
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Messages are persistent
    // ═══════════════════════════════════════════════════════════════

    "Messages are persistent" - {
        "should persist messages and reload them" {
            runTest {
                // given
                val session = chatSessionManager.createSession("Persistent Chat")
                val sessionId = session.metadata.id

                coEvery {
                    llmPort.chatWithMessages(
                        any(),
                        any(),
                        any()
                    )
                } returns io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Success(
                    """{"goalChange":null,"newTerms":[],"removedTermNames":[],"newConstraints":[],"removedConstraints":[],"newClarifiedFacts":[]}"""
                )

                val ragAnswer = RagAnswer(
                    answer = "Persisted answer.",
                    ragEnabled = false,
                    fallbackToPlain = true
                )
                coEvery { ragQueryProcessor.process(any(), any(), any()) } returns ragAnswer
                every { ragPromptBuilder.build(any(), any()) } returns "RAG context"

                // when — первый обмен
                chatExecutor.execute(
                    userInput = "First message",
                    sessionId = sessionId,
                    executionConfig = TaskExecutionConfig()
                )

                // Отдельный экземпляр репозитория (симуляция перезагрузки)
                val loaded = repository.loadById(sessionId).getOrNull()

                // then
                loaded shouldNotBe null
                loaded!!.messages.size shouldBe 2
                loaded.messages[0].shouldBeInstanceOf<ChatMessage.User>()
                (loaded.messages[0] as ChatMessage.User).text shouldBe "First message"
                loaded.messages[1].shouldBeInstanceOf<ChatMessage.Assistant>()
                (loaded.messages[1] as ChatMessage.Assistant).text shouldBe "Persisted answer."
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Graceful degradation: extractor fails
    // ═══════════════════════════════════════════════════════════════

    "Graceful degradation — extractor fails" - {
        "should generate answer even when extractor throws" {
            runTest {
                // given
                val session = chatSessionManager.createSession("Degradation Test")
                val sessionId = session.metadata.id

                // Extractor падает с ошибкой
                coEvery {
                    llmPort.chatWithMessages(
                        any(),
                        any(),
                        any()
                    )
                } throws RuntimeException("LLM extractor unavailable")

                // RAG работает нормально
                val ragAnswer = RagAnswer(
                    answer = "Answer despite extractor failure.",
                    ragEnabled = false,
                    fallbackToPlain = true
                )
                coEvery { ragQueryProcessor.process(any(), any(), any()) } returns ragAnswer
                every { ragPromptBuilder.build(any(), any()) } returns "RAG context"

                // when
                val result = chatExecutor.execute(
                    userInput = "Test message",
                    sessionId = sessionId,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "Answer despite extractor failure."
                result.taskStateUpdated shouldBe false
                result.stateDelta shouldBe null
                result.saveSucceeded shouldBe true
            }
        }

        "should generate answer when extractor returns failure Result" {
            runTest {
                // given
                val session = chatSessionManager.createSession("Extractor Failure Test")
                val sessionId = session.metadata.id

                // Extractor возвращает failure
                coEvery {
                    llmPort.chatWithMessages(
                        any(),
                        any(),
                        any()
                    )
                } returns io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Error("Parse error")

                val ragAnswer = RagAnswer(
                    answer = "Answer despite parse error.",
                    ragEnabled = false,
                    fallbackToPlain = true
                )
                coEvery { ragQueryProcessor.process(any(), any(), any()) } returns ragAnswer
                every { ragPromptBuilder.build(any(), any()) } returns "RAG context"

                // when
                val result = chatExecutor.execute(
                    userInput = "Another test",
                    sessionId = sessionId,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "Answer despite parse error."
                result.taskStateUpdated shouldBe false
                result.stateDelta shouldBe null
            }
        }
    }
})
