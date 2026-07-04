package io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rerank

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.DropReason
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.DroppedChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RerankResult
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RerankingStrategy
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Стратегия LLM-реранкинга: использует языковую модель для оценки релевантности чанков.
 *
 * Отправляет все чанки одним batch-запросом к LLM, получает оценки 0-10,
 * нормализует их в [0.0, 1.0] и сортирует по убыванию.
 *
 * При ошибке LLM (timeout, невалидный JSON) — fallback на [ThresholdReranker].
 */
class LlmReranker(
    private val llm: LlmPort,
    private val thresholdReranker: ThresholdReranker = ThresholdReranker()
) : RerankingStrategy {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun rerank(
        chunks: List<RelevantChunk>,
        query: String,
        config: SearchConfig
    ): RerankResult {
        if (chunks.isEmpty()) return RerankResult(emptyList(), emptyList(), 0)

        return try {
            val prompt = buildRerankPrompt(query, chunks)
            val response = llm.chat(Prompt(prompt), TaskExecutionConfig())
            val content = when (response) {
                is TaskResult.Success -> response.content
                is TaskResult.Error -> throw RuntimeException(response.message)
                is TaskResult.Partial -> response.content
            }
            val scores = parseRerankScores(content)

            val scored = chunks.mapIndexed { index, chunk ->
                chunk.copy(score = scores.getOrElse(index) { 5.0f } / 10.0f)
            }.sortedByDescending { it.score }

            val finalChunks = scored.take(config.topKFinal)
            val dropped = scored.drop(config.topKFinal).map {
                DroppedChunk(it, DropReason.LowRerankScore(it.score, config.threshold))
            }

            RerankResult(
                rankedChunks = finalChunks,
                droppedChunks = dropped,
                tokenUsage = prompt.length / 4
            )
        } catch (e: Exception) {
            System.err.println("[WARN] LLM rerank failed: ${e.message}, falling back to threshold")
            thresholdReranker.rerank(chunks, query, config)
        }
    }

    override suspend fun isAvailable(): Boolean = true

    private fun buildRerankPrompt(query: String, chunks: List<RelevantChunk>): String {
        val chunksText = chunks.mapIndexed { i, c ->
            "${i + 1}. ${c.chunk.text.take(300).replace("\n", " ")}"
        }.joinToString("\n")

        return """
            Оцени релевантность каждого чанка запросу от 0 до 10.
            
            Запрос: $query
            
            Чанки:
            $chunksText
            
            Ответь ТОЛЬКО в формате JSON: {"scores": [8, 6, 9, ...]}
        """.trimIndent()
    }

    private fun parseRerankScores(response: String): List<Float> {
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}') + 1
        if (jsonStart < 0 || jsonEnd <= jsonStart) throw RuntimeException("No JSON found in response")

        val jsonStr = response.substring(jsonStart, jsonEnd)
        val parsed = json.decodeFromString<RerankResponse>(jsonStr)
        return parsed.scores.map { it.toFloat() }
    }

    @Serializable
    private data class RerankResponse(val scores: List<Int>)
}
