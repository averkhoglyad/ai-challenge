package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.FallbackReason
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagAnswer
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
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
 * 3. Генерация embedding для вопроса через [EmbeddingGenerator]
 * 4. Поиск релевантных чанков через [VectorSearchPort]
 * 5. Сборка augmented-промпта через [RagPromptBuilder]
 * 6. Отправка в LLM через [LlmPort]
 *
 * ## Graceful degradation
 * При любых сбоях (нет индекса, пустой поиск, ошибка embedding/search/LLM)
 * выполняет fallback на обычный LLM-запрос, возвращая [RagAnswer.fallbackToPlain] = true.
 *
 * ## Логирование
 * Все ключевые шаги логируются на английском для отладки.
 *
 * @param embeddingGenerator генератор эмбеддингов (Infrastructure)
 * @param vectorSearchPort порт векторного поиска (Infrastructure)
 * @param promptBuilder сборщик RAG-промпта (Infrastructure)
 * @param llmPort порт LLM (Infrastructure)
 * @param indexRepository репозиторий индексов (Infrastructure)
 */
class RagQueryProcessor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val vectorSearchPort: VectorSearchPort,
    private val promptBuilder: RagPromptBuilder,
    private val llmPort: LlmPort,
    private val indexRepository: IndexRepository
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

        // Шаг 3: Генерация embedding для вопроса
        val queryEmbedding = try {
            val embeddings = embeddingGenerator.generateBatch(listOf(UUID.randomUUID() to question))
            embeddings.firstOrNull()?.vector
        } catch (e: Exception) {
            System.err.println("[ERROR] Failed to generate query embedding: ${e.message}")
            return plainLlmAnswer(question, config, ragEnabled = true, fallbackReason = FallbackReason.EMBEDDING_ERROR)
        }

        if (queryEmbedding == null) {
            System.err.println("[WARN] Embedding generator returned empty result")
            return plainLlmAnswer(question, config, ragEnabled = true, fallbackReason = FallbackReason.EMBEDDING_ERROR)
        }

        // Шаг 4: Поиск релевантных чанков
        val relevantChunks = try {
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

        System.err.println("[INFO] Vector search completed: found ${relevantChunks.size} chunks above threshold ${ragState.similarityThreshold}")

        // Шаг 5: Пустой поиск → fallback
        if (relevantChunks.isEmpty()) {
            return plainLlmAnswer(question, config, ragEnabled = true, fallbackReason = FallbackReason.EMPTY_SEARCH)
        }

        // Шаг 6: Сборка RAG-промпта
        val augmentedPrompt = promptBuilder.build(question, relevantChunks)

        // Шаг 7: LLM-запрос с контекстом
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

        // Шаг 8: Возврат результата с источниками
        val answer = when (result) {
            is TaskResult.Success -> result.content
            is TaskResult.Error -> "[Ошибка LLM] ${result.message}"
            is TaskResult.Partial -> result.content
        }

        System.err.println("[INFO] RAG query completed: ${relevantChunks.size} sources, answer length=${answer.length}")

        return RagAnswer(
            answer = answer,
            sources = relevantChunks,
            ragEnabled = true,
            fallbackToPlain = false,
            fallbackReason = null,
            llmError = null
        )
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
}
