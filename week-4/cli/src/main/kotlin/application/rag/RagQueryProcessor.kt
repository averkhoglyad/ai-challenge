package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RagPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.VectorSearchPort
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import java.util.*

class RagQueryProcessor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val vectorSearchPort: VectorSearchPort,
    private val promptBuilder: RagPromptBuilder,
    private val llmPort: LlmPort,
    private val indexRepository: IndexRepository,
    private val searchPipeline: SearchPipeline? = null,
    private val historyService: QueryHistoryService? = null,
    private val tokenEstimateCharsPerToken: Int = 4,
    private val relevanceChecker: RelevanceChecker = RelevanceChecker(),
    private val citationPromptBuilder: RagPromptBuilder? = null,
    private val answerParser: RagAnswerParser = RagAnswerParser(),
    private val answerValidator: RagAnswerValidator? = null,
    private val ragConfig: RagConfig = RagConfig()
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

        // Шаг 4: Проверка порога релевантности (Task 4: anti-hallucination)
        val relevanceCheck = relevanceChecker.check(relevantChunks, ragState.relevanceThreshold)
        if (relevanceCheck is RelevanceCheckResult.Insufficient) {
            return RagAnswer(
                answer = "Недостаточно релевантного контекста для ответа на вопрос.",
                sources = relevantChunks,
                ragEnabled = true,
                fallbackToPlain = false,
                fallbackReason = FallbackReason.INSUFFICIENT_RELEVANCE,
                searchContext = searchContext,
                isInsufficientContext = true,
                clarificationRequest = "Пожалуйста, уточните ваш вопрос или переформулируйте его.",
                maxRelevanceScore = relevanceCheck.maxScore,
                requiredThreshold = relevanceCheck.threshold
            )
        }

        // Шаг 5: Сборка RAG-промпта (citations-aware если доступен)
        val effectivePromptBuilder = citationPromptBuilder ?: promptBuilder
        val augmentedPrompt = effectivePromptBuilder.build(question, relevantChunks)

        // Шаг 6: LLM-запрос с контекстом
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

        val llmResponseText = when (result) {
            is TaskResult.Success -> result.content
            is TaskResult.Error -> "[Ошибка LLM] ${result.message}"
            is TaskResult.Partial -> result.content
        }

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

        // Шаг 7: Парсинг структурированного ответа (Task 4)
        val parsed = answerParser.parse(llmResponseText, relevantChunks)

        val ragAnswer = when (parsed) {
            is RagResult.Success -> RagAnswer(
                answer = parsed.answer,
                sources = relevantChunks,
                citations = parsed.citations,
                ragEnabled = true,
                fallbackToPlain = false,
                searchContext = finalSearchContext
            )

            is RagResult.InsufficientContext -> RagAnswer(
                answer = parsed.clarificationRequest ?: "Недостаточно контекста для ответа.",
                sources = relevantChunks,
                ragEnabled = true,
                fallbackToPlain = false,
                fallbackReason = FallbackReason.INSUFFICIENT_RELEVANCE,
                searchContext = finalSearchContext,
                isInsufficientContext = true,
                clarificationRequest = parsed.clarificationRequest
            )

            is RagResult.Fallback -> RagAnswer(
                answer = parsed.rawText,
                sources = relevantChunks,
                ragEnabled = true,
                fallbackToPlain = false,
                fallbackReason = FallbackReason.PARSE_ERROR,
                searchContext = finalSearchContext
            )
        }

        // Шаг 8: Валидация ответа (Task 4)
        val validationErrors = answerValidator?.validate(ragAnswer) ?: emptyList()
        if (validationErrors.isNotEmpty()) {
            System.err.println("[WARN] Answer validation failed: $validationErrors")
        }
        val validatedAnswer = ragAnswer.copy(validationErrors = validationErrors)

        System.err.println("[INFO] RAG query completed: ${relevantChunks.size} sources, citations=${ragAnswer.citations.size}, answer length=${ragAnswer.answer.length}")

        // Автоматическое сохранение в историю
        if (historyService != null && searchPipeline != null) {
            try {
                historyService.recordQuery(question, validatedAnswer, finalSearchContext)
            } catch (e: Exception) {
                System.err.println("[WARN] Failed to save query to history: ${e.message}")
            }
        }

        return validatedAnswer
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

    private fun estimateTokens(text: String): Int = text.length / tokenEstimateCharsPerToken
}
