package io.averkhogliad.ai.challenge.week4.cli.it.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagQueryProcessor
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommand
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository.IndexerDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository.SqliteIndexRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.prompt.SimpleRagPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.search.InMemoryCosineSearchAdapter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * E2E тесты RAG-функциональности.
 *
 * Используют in-memory SQLite + fake LLM/embedding для проверки полного флоу:
 * индексация → RAG toggle → запрос → источники.
 */
class RagE2ETest : FreeSpec({

    lateinit var tempDir: Path
    lateinit var database: SqliteDatabase
    lateinit var indexRepository: IndexRepository
    lateinit var queryProcessor: RagQueryProcessor
    lateinit var commandHandler: RagCommandHandler
    lateinit var llmPort: LlmPort
    val config = TaskExecutionConfig(temperature = 0.7, maxTokens = 500)

    fun createRun(
        runId: UUID,
        strategy: ChunkingStrategyType = ChunkingStrategyType.STRUCTURAL,
        sourcePath: String = "./docs",
        totalChunks: Int = 3
    ): IndexingRun = IndexingRun(
        id = runId,
        startedAt = Instant.now(),
        finishedAt = Instant.now(),
        strategy = strategy,
        sourcePath = sourcePath,
        chunkSize = null,
        overlap = null,
        embeddingModel = "test-model",
        status = RunStatus.COMPLETED,
        totalChunks = totalChunks,
        errorMessage = null,
        metadata = emptyMap()
    )

    fun indexedChunk(runId: UUID, text: String, source: String, vector: FloatArray): IndexedChunk {
        val chunk = Chunk(
            id = UUID.randomUUID(),
            runId = runId,
            contentHash = "hash-${text.hashCode()}",
            source = source,
            title = source.substringAfterLast('/'),
            section = null,
            text = text,
            strategy = ChunkingStrategyType.STRUCTURAL,
            metadata = emptyMap()
        )
        return IndexedChunk(chunk, Embedding(chunk.id, vector, "test-model"))
    }

    beforeEach {
        tempDir = Files.createTempDirectory("test-rag-e2e-")
        val dbPath = tempDir.resolve("test.db").toString()
        database = SqliteDatabase(dbPath)
        IndexerDatabase(database).initialize()
        indexRepository = SqliteIndexRepository(database)

        // Fake embedding generator — возвращает одинаковый вектор для всех текстов
        val embeddingGenerator = mockk<io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator>()
        coEvery { embeddingGenerator.generateBatch(any()) } answers {
            val batch = firstArg<List<Pair<UUID, String>>>()
            batch.map { (chunkId, _) ->
                Embedding(chunkId, floatArrayOf(0.5f, 0.3f, 0.2f), "test-model")
            }
        }

        val searchAdapter = InMemoryCosineSearchAdapter(indexRepository)
        val promptBuilder = SimpleRagPromptBuilder()

        // Fake LLM port — возвращает «ответ с контекстом»
        llmPort = mockk()
        coEvery { llmPort.chat(any(), any(), any()) } answers {
            TaskResult.Success("Ответ LLM на основе предоставленного контекста.")
        }

        queryProcessor = RagQueryProcessor(
            embeddingGenerator = embeddingGenerator,
            vectorSearchPort = searchAdapter,
            promptBuilder = promptBuilder,
            llmPort = llmPort,
            indexRepository = indexRepository
        )

        val renderer = RagCommandRenderer()
        commandHandler = RagCommandHandler(indexRepository, renderer)
    }

    afterEach {
        try {
            database.close()
        } catch (_: Exception) {
        }
        try {
            Files.deleteIfExists(tempDir)
        } catch (_: Exception) {
        }
    }

    "E2E RAG workflow" - {

        "full flow: indexing → :rag toggle → query → sources" {
            runTest {
                // Шаг 1: Создаём завершённый run с чанками
                val runId = UUID.randomUUID()
                val run = createRun(runId)
                indexRepository.createRun(run)

                // Сохраняем чанки с похожими векторами (чтобы поиск их нашёл)
                val chunks = listOf(
                    indexedChunk(
                        runId,
                        "Аутентификация через JWT токены",
                        "docs/api.md",
                        floatArrayOf(0.5f, 0.3f, 0.2f)
                    ),
                    indexedChunk(
                        runId,
                        "Токены действительны 24 часа",
                        "docs/auth.md",
                        floatArrayOf(0.5f, 0.3f, 0.19f)
                    ),
                    indexedChunk(
                        runId,
                        "Все эндпоинты требуют авторизацию",
                        "docs/security.md",
                        floatArrayOf(0.49f, 0.3f, 0.2f)
                    )
                )
                indexRepository.saveBatch(chunks)
                indexRepository.updateRunStatus(runId, RunStatus.COMPLETED, totalChunks = 3)

                // Шаг 2: Активируем индекс
                indexRepository.setActiveIndex(runId)

                // Шаг 3: Включаем RAG
                var state = CliState()
                state = commandHandler.handle(RagCommand.Toggle, state)
                state.ragState.enabled shouldBe true

                // Шаг 4: Делаем RAG-запрос
                val ragState = RagSessionState(enabled = true, topK = 3, similarityThreshold = 0.0f)
                val result = queryProcessor.process("Как работает аутентификация?", ragState, config)

                // Шаг 5: Проверяем результат
                result.ragEnabled shouldBe true
                result.fallbackToPlain shouldBe false
                result.sources.shouldNotBeEmpty()
                result.answer shouldBe "Ответ LLM на основе предоставленного контекста."
            }
        }

        "fallback: RAG enabled without active index → plain LLM" {
            runTest {
                val ragState = RagSessionState(enabled = true, topK = 3, similarityThreshold = 0.7f)

                val result = queryProcessor.process("Вопрос?", ragState, config)

                result.ragEnabled shouldBe true
                result.fallbackToPlain shouldBe true
                result.sources.shouldHaveSize(0)
            }
        }

        "toggle + status workflow" {
            runTest {
                var state = CliState()

                // Включаем RAG без индекса
                state = commandHandler.handle(RagCommand.Toggle, state)
                state.ragState.enabled shouldBe true

                // Проверяем статус
                state = commandHandler.handle(RagCommand.Status, state)
                state.ragState.enabled shouldBe true

                // Выключаем
                state = commandHandler.handle(RagCommand.Toggle, state)
                state.ragState.enabled shouldBe false

                // Проверяем статус после выключения
                state = commandHandler.handle(RagCommand.Status, state)
                state.ragState.enabled shouldBe false
            }
        }

        ":rag list shows empty when no runs" {
            runTest {
                val state = CliState()

                val newState = commandHandler.handle(RagCommand.List, state)

                newState shouldBe state
            }
        }

        ":rag list shows completed runs after indexing" {
            runTest {
                // Создаём completed run
                val runId = UUID.randomUUID()
                indexRepository.createRun(createRun(runId))
                indexRepository.updateRunStatus(runId, RunStatus.COMPLETED, totalChunks = 5)
                indexRepository.setActiveIndex(runId)

                val state = CliState()
                val newState = commandHandler.handle(RagCommand.List, state)

                newState shouldBe state
            }
        }
    }
})
