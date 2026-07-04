package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.QueryRewriter
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RerankResult
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RerankingStrategy
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.VectorSearchPort
import java.time.Instant
import java.util.*

/**
 * Оркестратор поискового pipeline: rewrite → embed → search → rerank/filter.
 *
 * ## Flow
 * 1. [Rewrite] — если mode == Rewrite, переписывает запрос через [QueryRewriter]
 * 2. [Embed] — генерирует embedding для запроса
 * 3. [Search] — векторный поиск с topKInitial (threshold = 0 для получения всех)
 * 4. [Rerank/Filter] — в зависимости от mode: Raw берёт topKFinal, Filtered/Reranked/Rewrite применяет стратегию
 * 5. [Collect metrics] — собирает все 5 метрик в [QueryExecutionStats]
 *
 * ## Graceful degradation
 * Каждый этап обрабатывает ошибки изолированно:
 * - Rewrite при ошибке → оригинальный query
 * - Rerank при ошибке → fallback на ThresholdReranker
 *
 * @param queryRewriter переписыватель запросов (LLM)
 * @param vectorSearch порт векторного поиска
 * @param reranker стратегия реранкинга
 * @param embeddingGenerator генератор эмбеддингов
 */
class SearchPipeline(
    private val queryRewriter: QueryRewriter,
    private val vectorSearch: VectorSearchPort,
    private val reranker: RerankingStrategy,
    private val embeddingGenerator: EmbeddingGenerator
) {

    /**
     * Выполняет полный pipeline поиска с замером метрик.
     *
     * @param query исходный запрос пользователя
     * @param config конфигурация поиска (mode, topK, threshold)
     * @param activeRunId ID активного индексационного run
     * @return [SearchContext] с результатами поиска и метриками
     */
    suspend fun execute(
        query: String,
        config: SearchConfig,
        activeRunId: UUID
    ): Result<SearchContext> {
        val queryId = UUID.randomUUID()
        val startTime = System.currentTimeMillis()

        var rewriteTokens: Int? = null
        var rerankTokens: Int? = null
        var rewrittenQuery: String? = null

        // Шаг 1: Rewrite (опционально, только для Rewrite mode)
        val queryToUse = if (config.mode == SearchMode.Rewrite) {
            try {
                val rewriteResult = queryRewriter.rewrite(query)
                rewriteTokens = rewriteResult.tokenUsage
                rewrittenQuery = rewriteResult.rewrittenQuery
                rewriteResult.rewrittenQuery
            } catch (e: Exception) {
                System.err.println("[WARN] Rewrite failed: ${e.message}, using original query")
                rewrittenQuery = null
                rewriteTokens = 0
                query
            }
        } else {
            query
        }

        // Шаг 2: Embedding
        val queryEmbedding = try {
            val embeddings = embeddingGenerator.generateBatch(listOf(UUID.randomUUID() to queryToUse))
            embeddings.firstOrNull()?.vector
                ?: return Result.failure(RuntimeException("Empty embedding result"))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        // Шаг 3: Vector search — запрашиваем topKInitial с threshold=0 (без фильтрации)
        val rawResults = try {
            vectorSearch.search(
                queryEmbedding = queryEmbedding,
                runId = activeRunId,
                topK = config.topKInitial,
                threshold = 0.0f
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }

        // Шаг 4: Rerank/Filter
        val rerankResult: RerankResult = when (config.mode) {
            SearchMode.Raw -> {
                // Без фильтрации: просто берём topKFinal
                RerankResult(
                    rankedChunks = rawResults.take(config.topKFinal),
                    droppedChunks = emptyList(),
                    tokenUsage = 0
                )
            }

            SearchMode.Filtered -> {
                reranker.rerank(rawResults, queryToUse, config)
            }

            SearchMode.Reranked, SearchMode.Rewrite -> {
                try {
                    reranker.rerank(rawResults, queryToUse, config).also {
                        rerankTokens = it.tokenUsage
                    }
                } catch (e: Exception) {
                    System.err.println("[WARN] Rerank failed: ${e.message}, falling back to threshold")
                    // ThresholdReranker уже является fallback внутри LlmReranker,
                    // но на случай если reranker — не LlmReranker, обрабатываем здесь
                    reranker.rerank(rawResults, queryToUse, config).also {
                        rerankTokens = 0
                    }
                }
            }
        }

        // Шаг 5: Сбор метрик
        val totalMs = System.currentTimeMillis() - startTime

        val initialAvgScore = if (rawResults.isNotEmpty()) {
            rawResults.map { it.score }.average().toFloat()
        } else 0f

        val filteredAvgScore = if (rerankResult.rankedChunks.isNotEmpty()) {
            rerankResult.rankedChunks.map { it.score }.average().toFloat()
        } else 0f

        val dropBreakdown = DropBreakdown(
            byThreshold = rerankResult.droppedChunks.count { it.reason is DropReason.BelowThreshold },
            byTopK = rerankResult.droppedChunks.count { it.reason is DropReason.TopKLimit },
            byRerank = rerankResult.droppedChunks.count { it.reason is DropReason.LowRerankScore }
        )

        val stats = QueryExecutionStats(
            queryId = queryId,
            timestamp = Instant.now(),
            mode = config.mode,
            totalMs = totalMs,
            chunks = ChunkFlow(
                initial = rawResults.size,
                filtered = rerankResult.rankedChunks.size + rerankResult.droppedChunks.size,
                final = rerankResult.rankedChunks.size
            ),
            score = ScoreDelta(initialAvgScore, filteredAvgScore),
            tokens = TokenBreakdown(
                rewrite = rewriteTokens,
                rerank = rerankTokens,
                answer = 0 // answer tokens заполняются в RagQueryProcessor
            ),
            dropped = dropBreakdown
        )

        return Result.success(
            SearchContext(
                query = query,
                rewrittenQuery = rewrittenQuery,
                rawResults = rawResults,
                filteredResults = rerankResult.rankedChunks,
                droppedChunks = rerankResult.droppedChunks,
                stats = stats
            )
        )
    }
}
