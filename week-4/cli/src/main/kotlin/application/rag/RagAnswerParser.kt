package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.Citation
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import kotlinx.serialization.json.*

class RagAnswerParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Парсит ответ LLM в структурированный [RagResult].
     *
     * Распознаёт:
     * - Маркер `INSUFFICIENT_CONTEXT` → [RagResult.InsufficientContext]
     * - JSON с полями `answer`, `citations_used` → [RagResult.Success]
     * - Любой другой текст → [RagResult.Fallback] (plain text без цитат)
     *
     * @param llmResponse сырой текст ответа от LLM
     * @param chunks список релевантных чанков для построения цитат
     * @return структурированный результат парсинга
     */
    fun parse(llmResponse: String, chunks: List<RelevantChunk>): RagResult {
        if (llmResponse.contains("INSUFFICIENT_CONTEXT")) {
            val clarification = Regex("""INSUFFICIENT_CONTEXT:\s*(.+)""")
                .find(llmResponse)?.groupValues?.getOrNull(1)
            return RagResult.InsufficientContext(clarification)
        }

        return try {
            val jsonObj = json.parseToJsonElement(llmResponse).jsonObject
            val answer = jsonObj["answer"]?.jsonPrimitive?.contentOrNull ?: llmResponse
            val citationsUsed = jsonObj["citations_used"]?.jsonArray
                ?.map { it.jsonPrimitive.int } ?: emptyList()

            val citations = citationsUsed.mapNotNull { index ->
                chunks.getOrNull(index - 1)?.let { chunk ->
                    Citation(
                        chunkId = chunk.chunk.id.toString(),
                        text = chunk.chunk.text,
                        source = chunk.chunk.source,
                        relevanceScore = chunk.score,
                        section = chunk.chunk.section
                    )
                }
            }

            RagResult.Success(answer, citations)
        } catch (_: Exception) {
            RagResult.Fallback(llmResponse)
        }
    }
}

/**
 * Результат парсинга ответа LLM в [RagAnswerParser].
 */
sealed interface RagResult {
    /** Успешный парсинг JSON: извлечены ответ и цитаты. */
    data class Success(
        val answer: String,
        val citations: List<Citation>
    ) : RagResult

    /** LLM сообщила о недостаточности контекста. */
    data class InsufficientContext(
        val clarificationRequest: String?
    ) : RagResult

    /** Fallback: невалидный JSON — возвращается сырой текст без цитат. */
    data class Fallback(
        val rawText: String
    ) : RagResult
}
