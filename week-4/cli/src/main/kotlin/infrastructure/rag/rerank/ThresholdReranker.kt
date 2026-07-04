package io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rerank

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.DropReason
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.DroppedChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RerankResult
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RerankingStrategy

/**
 * Стратегия реранкинга на основе порогового значения cosine similarity.
 *
 * Отбрасывает чанки с [score] < [SearchConfig.threshold],
 * затем оставляет top-K_final лучших.
 *
 * Не использует LLM — [tokenUsage] всегда 0.
 * [isAvailable] всегда возвращает true.
 */
class ThresholdReranker : RerankingStrategy {

    override suspend fun rerank(
        chunks: List<RelevantChunk>,
        query: String,
        config: SearchConfig
    ): RerankResult {
        val threshold = config.threshold

        val aboveThreshold = chunks.filter { it.score >= threshold }
        val droppedByThreshold = chunks.filter { it.score < threshold }
            .map { DroppedChunk(it, DropReason.BelowThreshold(threshold)) }

        val finalChunks = aboveThreshold.take(config.topKFinal)
        val droppedByTopK = aboveThreshold.drop(config.topKFinal)
            .map { DroppedChunk(it, DropReason.TopKLimit) }

        return RerankResult(
            rankedChunks = finalChunks,
            droppedChunks = droppedByThreshold + droppedByTopK,
            tokenUsage = 0
        )
    }

    override suspend fun isAvailable(): Boolean = true
}
