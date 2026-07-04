package io.averkhogliad.ai.challenge.week4.cli.domain.rag.port

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.DroppedChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig

/**
 * Порт стратегии реранкинга чанков.
 *
 * Определяет контракт между application-слоем (SearchPipeline) и
 * infrastructure-слоем (ThresholdReranker, LlmReranker).
 *
 * Принцип инверсии зависимостей (DIP): domain определяет интерфейс,
 * infrastructure его реализует.
 */
interface RerankingStrategy {

    /**
     * Переоценивает релевантность чанков запросу.
     *
     * @param chunks список чанков после векторного поиска
     * @param query оригинальный (или переписанный) запрос
     * @param config конфигурация поиска (threshold, topKFinal)
     * @return результат реранкинга: отсортированные чанки, отброшенные чанки, использованные токены
     */
    suspend fun rerank(
        chunks: List<RelevantChunk>,
        query: String,
        config: SearchConfig
    ): RerankResult

    /**
     * Проверяет доступность стратегии.
     * Например, LlmReranker проверяет health-check LLM.
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Результат выполнения стратегии реранкинга.
 *
 * @property rankedChunks чанки после реранкинга, отсортированные по убыванию score
 * @property droppedChunks отброшенные чанки с причинами отсева
 * @property tokenUsage количество токенов, использованных при реранкинге (0 для ThresholdReranker)
 */
data class RerankResult(
    val rankedChunks: List<RelevantChunk>,
    val droppedChunks: List<DroppedChunk>,
    val tokenUsage: Int
)
