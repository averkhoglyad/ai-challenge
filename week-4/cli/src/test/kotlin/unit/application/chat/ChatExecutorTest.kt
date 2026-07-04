package io.averkhogliad.ai.challenge.week4.cli.unit.application.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatExecutor
import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager
import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagQueryProcessor
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagAnswer
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatNameGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskStateExtractor
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

/**
 * Тесты для [ChatExecutor] — pipeline обработки пользовательского сообщения.
 */
class ChatExecutorTest : FreeSpec({

    // ──── Fake repository for tests ────
    fun fakeRepository(session: ChatSession): ChatSessionRepository {
        val repo = mockk<ChatSessionRepository>(relaxed = true)
        coEvery { repo.loadById(session.metadata.id) } returns Result.success(session)
        coEvery { repo.save(any()) } answers { Result.success(firstArg<ChatSession>()) }
        return repo
    }

    // ──── Helpers ────
    fun createSession(): ChatSession = ChatSession.create(config = ChatConfig())

    fun createExecutor(
        taskStateExtractor: TaskStateExtractor,
        ragQueryProcessor: RagQueryProcessor,
        sessionRepository: ChatSessionRepository,
        chatPromptBuilder: ChatPromptBuilder,
        chatNameGenerator: ChatNameGenerator,
        chatSessionManager: ChatSessionManager = mockk<ChatSessionManager>().also {
            coEvery { it.maybeAutoName(any()) } answers { firstArg() }
        }
    ) = ChatExecutor(
        taskStateExtractor = taskStateExtractor,
        ragQueryProcessor = ragQueryProcessor,
        chatSessionRepository = sessionRepository,
        chatSessionManager = chatSessionManager,
        chatPromptBuilder = chatPromptBuilder,
        chatNameGenerator = chatNameGenerator,
        config = ChatConfig()
    )

    "Successful full cycle" - {

        "should process user input and return ChatResult" {
            runTest {
                // given
                val session = createSession()
                val repo = fakeRepository(session)
                val extractor = mockk<TaskStateExtractor>()
                val ragProcessor = mockk<RagQueryProcessor>()
                val promptBuilder = mockk<ChatPromptBuilder>()
                val nameGenerator = mockk<ChatNameGenerator>()

                val delta = TaskStateDelta.SetGoal("Test Goal")
                coEvery { extractor.extract(any(), any()) } returns Result.success(delta)

                val ragAnswer = RagAnswer(
                    answer = "Test response",
                    ragEnabled = true,
                    fallbackToPlain = false
                )
                coEvery { ragProcessor.process(any(), any(), any()) } returns ragAnswer

                every { promptBuilder.build(any(), any(), any(), any()) } returns "Formatted prompt"

                // Disable auto-name to simplify
                val executor = createExecutor(extractor, ragProcessor, repo, promptBuilder, nameGenerator)
                val executorWithDisabledAutoName = ChatExecutor(
                    taskStateExtractor = extractor,
                    ragQueryProcessor = ragProcessor,
                    chatSessionRepository = repo,
                    chatSessionManager = mockk<ChatSessionManager>().also {
                        coEvery { it.maybeAutoName(any()) } answers { firstArg() }
                    },
                    chatPromptBuilder = promptBuilder,
                    chatNameGenerator = nameGenerator,
                    config = ChatConfig(autoNameEnabled = false)
                )

                // when
                val result = executorWithDisabledAutoName.execute(
                    userInput = "Hello",
                    sessionId = session.metadata.id,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "Test response"
                result.session.messages.size shouldBe 2 // user + assistant
                result.taskStateUpdated shouldBe true
                result.stateDelta shouldBe delta
            }
        }
    }

    "Graceful degradation — Extractor fails" - {

        "should continue without memory update when extractor throws" {
            runTest {
                // given
                val session = createSession()
                val repo = fakeRepository(session)
                val extractor = mockk<TaskStateExtractor>()
                val ragProcessor = mockk<RagQueryProcessor>()
                val promptBuilder = mockk<ChatPromptBuilder>()
                val nameGenerator = mockk<ChatNameGenerator>()

                coEvery { extractor.extract(any(), any()) } throws RuntimeException("LLM unavailable")

                val ragAnswer = RagAnswer(
                    answer = "Response without memory",
                    ragEnabled = true,
                    fallbackToPlain = false
                )
                coEvery { ragProcessor.process(any(), any(), any()) } returns ragAnswer
                every { promptBuilder.build(any(), any(), any(), any()) } returns "Formatted prompt"

                val executor = ChatExecutor(
                    taskStateExtractor = extractor,
                    ragQueryProcessor = ragProcessor,
                    chatSessionRepository = repo,
                    chatSessionManager = mockk<ChatSessionManager>().also {
                        coEvery { it.maybeAutoName(any()) } answers { firstArg() }
                    },
                    chatPromptBuilder = promptBuilder,
                    chatNameGenerator = nameGenerator,
                    config = ChatConfig(autoNameEnabled = false)
                )

                // when
                val result = executor.execute(
                    userInput = "Hello",
                    sessionId = session.metadata.id,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "Response without memory"
                result.taskStateUpdated shouldBe false
                result.stateDelta shouldBe null
            }
        }

        "should continue when extractor returns failure Result" {
            runTest {
                // given
                val session = createSession()
                val repo = fakeRepository(session)
                val extractor = mockk<TaskStateExtractor>()
                val ragProcessor = mockk<RagQueryProcessor>()
                val promptBuilder = mockk<ChatPromptBuilder>()
                val nameGenerator = mockk<ChatNameGenerator>()

                coEvery { extractor.extract(any(), any()) } returns Result.failure(Exception("Parse error"))

                val ragAnswer = RagAnswer(
                    answer = "Response",
                    ragEnabled = true,
                    fallbackToPlain = false
                )
                coEvery { ragProcessor.process(any(), any(), any()) } returns ragAnswer
                every { promptBuilder.build(any(), any(), any(), any()) } returns "Formatted prompt"

                val executor = ChatExecutor(
                    taskStateExtractor = extractor,
                    ragQueryProcessor = ragProcessor,
                    chatSessionRepository = repo,
                    chatSessionManager = mockk<ChatSessionManager>().also {
                        coEvery { it.maybeAutoName(any()) } answers { firstArg() }
                    },
                    chatPromptBuilder = promptBuilder,
                    chatNameGenerator = nameGenerator,
                    config = ChatConfig(autoNameEnabled = false)
                )

                // when
                val result = executor.execute(
                    userInput = "Hello",
                    sessionId = session.metadata.id,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "Response"
                result.taskStateUpdated shouldBe false
            }
        }
    }

    "Graceful degradation — RAG fails" - {

        "should return fallback answer when RAG throws" {
            runTest {
                // given
                val session = createSession()
                val repo = fakeRepository(session)
                val extractor = mockk<TaskStateExtractor>()
                val ragProcessor = mockk<RagQueryProcessor>()
                val promptBuilder = mockk<ChatPromptBuilder>()
                val nameGenerator = mockk<ChatNameGenerator>()

                coEvery { extractor.extract(any(), any()) } returns Result.success(TaskStateDelta.NoChanges)
                coEvery { ragProcessor.process(any(), any(), any()) } throws RuntimeException("RAG down")
                every { promptBuilder.build(any(), any(), any(), any()) } returns "Formatted prompt"

                val executor = ChatExecutor(
                    taskStateExtractor = extractor,
                    ragQueryProcessor = ragProcessor,
                    chatSessionRepository = repo,
                    chatSessionManager = mockk<ChatSessionManager>().also {
                        coEvery { it.maybeAutoName(any()) } answers { firstArg() }
                    },
                    chatPromptBuilder = promptBuilder,
                    chatNameGenerator = nameGenerator,
                    config = ChatConfig(autoNameEnabled = false)
                )

                // when
                val result = executor.execute(
                    userInput = "Hello",
                    sessionId = session.metadata.id,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "Я не знаю"
            }
        }
    }

    "Graceful degradation — NameGenerator fails" - {

        "should keep current name when name generator fails" {
            runTest {
                // given
                val session = createSession()
                val repo = fakeRepository(session)
                val extractor = mockk<TaskStateExtractor>()
                val ragProcessor = mockk<RagQueryProcessor>()
                val promptBuilder = mockk<ChatPromptBuilder>()
                val nameGenerator = mockk<ChatNameGenerator>()

                coEvery { extractor.extract(any(), any()) } returns Result.success(TaskStateDelta.NoChanges)

                val ragAnswer = RagAnswer(
                    answer = "Response",
                    ragEnabled = true,
                    fallbackToPlain = false
                )
                coEvery { ragProcessor.process(any(), any(), any()) } returns ragAnswer
                every { promptBuilder.build(any(), any(), any(), any()) } returns "Formatted prompt"

                // NameGenerator will throw → graceful degradation
                coEvery { nameGenerator.generate(any()) } throws RuntimeException("Name generation failed")

                val executor = createExecutor(extractor, ragProcessor, repo, promptBuilder, nameGenerator)

                // when
                val result = executor.execute(
                    userInput = "Hello",
                    sessionId = session.metadata.id,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "Response"
                result.session.metadata.name shouldBe "New Chat" // unchanged
            }
        }
    }

    "Auto-naming after first exchange" - {

        "should generate name when autoNameEnabled and first exchange" {
            runTest {
                // given
                val session = createSession()
                val repo = fakeRepository(session)
                val extractor = mockk<TaskStateExtractor>()
                val ragProcessor = mockk<RagQueryProcessor>()
                val promptBuilder = mockk<ChatPromptBuilder>()
                val nameGenerator = mockk<ChatNameGenerator>()

                coEvery { extractor.extract(any(), any()) } returns Result.success(TaskStateDelta.NoChanges)

                val ragAnswer = RagAnswer(
                    answer = "Assistant response",
                    ragEnabled = true,
                    fallbackToPlain = false
                )
                coEvery { ragProcessor.process(any(), any(), any()) } returns ragAnswer
                every { promptBuilder.build(any(), any(), any(), any()) } returns "Formatted prompt"

                coEvery { nameGenerator.generate(any()) } returns Result.success("API Discussion")

                val chatSessionManager = mockk<ChatSessionManager>()
                coEvery { chatSessionManager.maybeAutoName(any()) } answers {
                    val s = firstArg<ChatSession>()
                    s.rename("API Discussion", generated = true)
                }

                val executor =
                    createExecutor(extractor, ragProcessor, repo, promptBuilder, nameGenerator, chatSessionManager)

                // when
                val result = executor.execute(
                    userInput = "Tell me about REST APIs",
                    sessionId = session.metadata.id,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.session.metadata.name shouldBe "API Discussion"
                result.session.metadata.nameGenerated shouldBe true
            }
        }

        "should not auto-name when already generated" {
            runTest {
                // given
                val session = ChatSession.create(config = ChatConfig())
                    .rename("Already Named", generated = true)
                val repo = fakeRepository(session)
                val extractor = mockk<TaskStateExtractor>()
                val ragProcessor = mockk<RagQueryProcessor>()
                val promptBuilder = mockk<ChatPromptBuilder>()
                val nameGenerator = mockk<ChatNameGenerator>()

                coEvery { extractor.extract(any(), any()) } returns Result.success(TaskStateDelta.NoChanges)

                val ragAnswer = RagAnswer(
                    answer = "Response",
                    ragEnabled = true,
                    fallbackToPlain = false
                )
                coEvery { ragProcessor.process(any(), any(), any()) } returns ragAnswer
                every { promptBuilder.build(any(), any(), any(), any()) } returns "Formatted prompt"

                val executor = createExecutor(extractor, ragProcessor, repo, promptBuilder, nameGenerator)

                // when
                val result = executor.execute(
                    userInput = "Hello again",
                    sessionId = session.metadata.id,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.session.metadata.name shouldBe "Already Named"
                coVerify(exactly = 0) { nameGenerator.generate(any()) }
            }
        }
    }

    "TaskState extraction disabled" - {

        "should skip extraction when disabled in config" {
            runTest {
                // given
                val session = createSession()
                val repo = fakeRepository(session)
                val extractor = mockk<TaskStateExtractor>()
                val ragProcessor = mockk<RagQueryProcessor>()
                val promptBuilder = mockk<ChatPromptBuilder>()
                val nameGenerator = mockk<ChatNameGenerator>()

                val ragAnswer = RagAnswer(
                    answer = "Response",
                    ragEnabled = true,
                    fallbackToPlain = false
                )
                coEvery { ragProcessor.process(any(), any(), any()) } returns ragAnswer
                every { promptBuilder.build(any(), any(), any(), any()) } returns "Formatted prompt"

                val executor = ChatExecutor(
                    taskStateExtractor = extractor,
                    ragQueryProcessor = ragProcessor,
                    chatSessionRepository = repo,
                    chatSessionManager = mockk<ChatSessionManager>().also {
                        coEvery { it.maybeAutoName(any()) } answers { firstArg() }
                    },
                    chatPromptBuilder = promptBuilder,
                    chatNameGenerator = nameGenerator,
                    config = ChatConfig(
                        taskStateExtractionEnabled = false,
                        autoNameEnabled = false
                    )
                )

                // when
                val result = executor.execute(
                    userInput = "Hello",
                    sessionId = session.metadata.id,
                    executionConfig = TaskExecutionConfig()
                )

                // then
                result.answer shouldBe "Response"
                result.taskStateUpdated shouldBe false
                coVerify(exactly = 0) { extractor.extract(any(), any()) }
            }
        }
    }
})
