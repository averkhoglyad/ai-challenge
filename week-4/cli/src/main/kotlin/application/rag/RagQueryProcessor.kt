package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.FallbackReason
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagAnswer
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchContext
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RagPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.VectorSearchPort
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import java.util.*

/**
 * Оркестратор RAG-запроса.
 *
 * Реализует полный флоу Retrieval-Augmented Generation:
 * 1. Проверка состояния RAG ([RagSessionState.enabled])
 * 2. Получение активного индекса из [IndexRepository]
 * 3. Выполнение поискового pipeline через [SearchPipeline] (если доступен)
 *    или прямой векторный поиск через [VectorSearchPort] (legacy)
 * 4. Сборка augmented-промпта через [RagPromptBuilder]
 * 5. Отправка в LLM через [LlmPort]
 *
 * ## SearchPipeline vs Legacy
 * Если [searchPipeline] задан, используется новый флоу с rewrite→search→rerank→filter.
 * Иначе — legacy-флоу с прямым векторным поиском (обратная совместимость).
 *
 * ## Graceful degradation
 * При любых сбоях (нет индекса, пустой поиск, ошибка embedding/search/LLM)
 * выполняет fallback на обычный LLM-запрос, возвращая [RagAnswer.fallbackToPlain] = true.
 *
 * @param embeddingGenerator генератор эмбеддингов (Infrastructure)
 * @param vectorSearchPort порт векторного поиска (Infrastructure)
 * @param promptBuilder сборщик RAG-промпта (Infrastructure)
 * @param llmPort порт LLM (Infrastructure)
 * @param indexRepository репозиторий индексов (Infrastructure)
 * @param searchPipeline опциональный поисковый pipeline с rewrite/rerank/filter
 * @param historyService опциональный сервис истории запросов
 */
class RagQueryProcessor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val vectorSearchPort: VectorSearchPort,
    private val promptBuilder: RagPromptBuilder,
    private val llmPort: LlmPort,
    private val indexRepository: IndexRepository,
    private val searchPipeline: SearchPipeline? = null,
    private val historyService: QueryHistoryService? = null
) {

    /**
     * Выполняет RAG-запрос или fallback на plain LLM.
     *
     * @param question вопрос пользователя
     * @param ragState текущее состояние RAG-сессии
     * @param config конфигурация выполнения (temperature, maxTokens, modelId)
     * @return [RagAnswer] с ответом LLM, источниками (если RAG успешен) и флагами состояния
     */
    suspend fun process(
        question: String,
        ragState: RagSessionState,
        config: TaskExecutionConfig
    ): RagAnswer {
        // Шаг 1: RAG выключен → plain LLM
        if (!ragState.enabled) {
            return plainLlmAnswer(question, config, ragEnabled = false, fallbackReason = FallbackReason.RAG_DISABLED)
        }

        // Шаг 2: RAG включен, но нет активного индекса → fallback
        val activeRunId: UUID = indexRepository.getActiveIndex()
            ?: return plainLlmAnswer(
                question,
                config,
                ragEnabled = true,
                fallbackReason = FallbackReason.NO_ACTIVE_INDEX
            )

        // Шаг 3: Выполнение поиска (новый pipeline или legacy)
        val searchContext: SearchContext
        val relevantChunks: List<io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk>

        if (searchPipeline != null) {
            // Новый флоу: SearchPipeline
            val pipelineResult = searchPipeline.execute(question, ragState.config, activeRunId)
            if (pipelineResult.isFailure) {
                System.err.println("[ERROR] Search pipeline failed: ${pipelineResult.exceptionOrNull()?.message}")
                return plainLlmAnswer(question, config, ragEnabled = true, fallbackReason = FallbackReason.SEARCH_ERROR)
            }
            searchContext = pipelineResult.getOrThrow()
            if (searchContext.filteredResults.isEmpty()) {
                return plainLlmAnswer(question, config, ragEnabled = true, fallbackReason = FallbackReason.EMPTY_SEARCH)
            }
            relevantChunks = searchContext.filteredResults
        } else {
            // Legacy флоу: прямой векторный поиск
            searchContext = SearchContext(
                query = question,
                rewrittenQuery = null,
                rawResults = emptyList(),
                filteredResults = emptyList(),
                droppedChunks = emptyList(),
                stats = io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.QueryExecutionStats(
                    queryId = UUID.randomUUID(),
                    timestamp = java.time.Instant.now(),
                    mode = io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode.Filtered,
                    totalMs = 0,
                    chunks = io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.ChunkFlow(0, 0, 0),
                    score = io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.ScoreDelta(0f, 0f),
                    tokens = io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.TokenBreakdown(null, null, 0),
                    dropped = io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.DropBreakdown(0, 0, 0)
                )
            )

            val queryEmbedding = try {
                val embeddings = embeddingGenerator.generateBatch(listOf(UUID.randomUUID() to question))
                embeddings.firstOrNull()?.vector
            } catch (e: Exception) {
                System.err.println("[ERROR] Failed to generate query embedding: ${e.message}")
                return plainLlmAnswer(
                    question,
                    config,
                    ragEnabled = true,
                    fallbackReason = FallbackReason.EMBEDDING_ERROR
                )
            }

            if (queryEmbedding == null) {
                System.err.println("[WARN] Embedding generator returned empty result")
                return plainLlmAnswer(
                    question,
                    config,
                    ragEnabled = true,
                    fallbackReason = FallbackReason.EMBEDDING_ERROR
                )
            }

            relevantChunks = try {
                vectorSearchPort.search(
                    queryEmbedding = queryEmbedding,
                    runId = activeRunId,
                    topK = ragState.topK,
                    threshold = ragState.similarityThreshold
                )
            } catch (e: Exception) {
                System.err.println("[ERROR] Vector search failed: ${e.message}")
                return plainLlmAnswer(question, config, ragEnabled = true, fallbackReason = FallbackReason.SEARCH_ERROR)
            }

            if (relevantChunks.isEmpty()) {
                return plainLlmAnswer(question, config, ragEnabled = true, fallbackReason = FallbackReason.EMPTY_SEARCH)
            }
        }

        // Шаг 4: Сборка RAG-промпта
        val augmentedPrompt = promptBuilder.build(question, relevantChunks)

        // Шаг 5: LLM-запрос с контекстом
        val llmStartTime = System.currentTimeMillis()
        val result = try {
            llmPort.chat(Prompt(augmentedPrompt), config)
        } catch (e: Exception) {
            System.err.println("[ERROR] LLM request failed: ${e.message}")
            return plainLlmAnswer(
                question,
                config,
                ragEnabled = true,
                fallbackReason = FallbackReason.LLM_ERROR,
                llmError = e.message
            )
        }
        val answerTokens = estimateTokens(augmentedPrompt)

        // Обновляем токены в searchContext (если был pipeline)
        val finalSearchContext = if (searchPipeline != null) {
            val updatedTokens = searchContext.stats.tokens.copy(answer = answerTokens)
            val updatedStats = searchContext.stats.copy(
                tokens = updatedTokens,
                totalMs = searchContext.stats.totalMs + (System.currentTimeMillis() - llmStartTime)
            )
            searchContext.copy(stats = updatedStats)
        } else {
            searchContext
        }

        // Шаг 6: Возврат результата с источниками
        val answer = when (result) {
            is TaskResult.Success -> result.content
            is TaskResult.Error -> "[Ошибка LLM] ${result.message}"
            is TaskResult.Partial -> result.content
        }

        System.err.println("[INFO] RAG query completed: ${relevantChunks.size} sources, answer length=${answer.length}")

        val ragAnswer = RagAnswer(
            answer = answer,
            sources = relevantChunks,
            ragEnabled = true,
            fallbackToPlain = false,
            fallbackReason = null,
            llmError = null,
            searchContext = finalSearchContext
        )

        // Автоматическое сохранение в историю
        if (historyService != null && searchPipeline != null) {
            try {
                historyService.recordQuery(question, ragAnswer, finalSearchContext)
            } catch (e: Exception) {
                System.err.println("[WARN] Failed to save query to history: ${e.message}")
            }
        }

        return ragAnswer
    }

    /**
     * Выполняет обычный LLM-запрос (без RAG-контекста).
     */
    private suspend fun plainLlmAnswer(
        question: String,
        config: TaskExecutionConfig,
        ragEnabled: Boolean,
        fallbackReason: FallbackReason? = null,
        llmError: String? = null
    ): RagAnswer {
        if (fallbackReason != null) {
            System.err.println("[WARN] Falling back to plain LLM: reason=$fallbackReason")
        }

        val result = try {
            llmPort.chat(Prompt(question), config)
        } catch (e: Exception) {
            System.err.println("[ERROR] Plain LLM request failed: ${e.message}")
            return RagAnswer(
                answer = "[Ошибка LLM] ${e.message}",
                ragEnabled = ragEnabled,
                fallbackToPlain = true,
                fallbackReason = FallbackReason.LLM_ERROR,
                llmError = e.message
            )
        }

        val answer = when (result) {
            is TaskResult.Success -> result.content
            is TaskResult.Error -> "[Ошибка LLM] ${result.message}"
            is TaskResult.Partial -> result.content
        }

        return RagAnswer(
            answer = answer,
            ragEnabled = ragEnabled,
            fallbackToPlain = fallbackReason != null && fallbackReason != FallbackReason.RAG_DISABLED,
            fallbackReason = fallbackReason
        )
    }

    private fun estimateTokens(text: String): Int = text.length / 4
}
