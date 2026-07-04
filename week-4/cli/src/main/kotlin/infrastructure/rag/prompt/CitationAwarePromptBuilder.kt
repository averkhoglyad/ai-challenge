package io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.prompt

import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RagPromptBuilder

/**
 * Реализация [RagPromptBuilder] с требованиями анти-галлюцинаций (Task 4).
 *
 * Формирует промпт с:
 * - Нумерованными цитатами [1], [2], ...
 * - Явными инструкциями использовать ТОЛЬКО предоставленный контекст
 * - Требованием JSON-формата ответа с полями answer, citations_used, confidence
 * - Инструкцией для режима «не знаю» (INSUFFICIENT_CONTEXT)
 *
 * Ограничивает количество цитат до [RagConfig.maxCitationsPerAnswer].
 *
 * @property config конфигурация RAG с параметрами анти-галлюцинаций
 */
class CitationAwarePromptBuilder(
    private val config: RagConfig
) : RagPromptBuilder {

    override fun build(question: String, context: List<RelevantChunk>): String {
        if (context.isEmpty()) return question

        val citations = context
            .take(config.maxCitationsPerAnswer)
            .mapIndexed { index, chunk ->
                val sourceInfo = if (chunk.chunk.section != null) {
                    "${chunk.chunk.source} (${chunk.chunk.section})"
                } else {
                    chunk.chunk.source
                }
                "[${index + 1}] ${chunk.chunk.text}\n   Источник: $sourceInfo, Чанк: ${chunk.chunk.id}"
            }
            .joinToString("\n\n")

        return """
            |Ты — ассистент, который отвечает СТРОГО на основе предоставленных цитат.
            |
            |ЦИТАТЫ:
            |$citations
            |
            |ИНСТРУКЦИИ:
            |1. Отвечай на вопрос, используя ТОЛЬКО информацию из цитат выше.
            |2. В тексте ответа обязательно указывай номера цитат в квадратных скобках: [1], [2] и т.д.
            |3. Если цитаты НЕ содержат достаточно информации для ответа, ответь ровно так:
            |   INSUFFICIENT_CONTEXT: Я не могу ответить на этот вопрос на основе предоставленных источников. Пожалуйста, уточните ваш вопрос.
            |4. НЕ придумывай информацию, которой нет в цитатах.
            |5. НЕ используй свои общие знания — только цитаты.
            |
            |ВОПРОС: $question
            |
            |ОТВЕТ (строго в JSON-формате):
            |{
            |  "answer": "твой ответ со ссылками [1], [2]",
            |  "citations_used": [1, 2],
            |  "confidence": 0.95
            |}
        """.trimMargin()
    }
}
