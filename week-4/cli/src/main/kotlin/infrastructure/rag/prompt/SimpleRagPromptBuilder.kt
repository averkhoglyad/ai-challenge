package io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.prompt

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RagPromptBuilder

/**
 * Простая реализация [RagPromptBuilder].
 *
 * Формирует промпт в формате:
 * 1. Инструкция: "Ответь на вопрос, основываясь на следующем контексте:"
 * 2. Контекст из релевантных чанков с указанием источника
 * 3. Вопрос пользователя
 * 4. Fallback-фраза: "Если ответа нет в контексте, скажи ..."
 *
 * Если контекст пуст, возвращает только вопрос пользователя без дополнительных инструкций.
 */
class SimpleRagPromptBuilder : RagPromptBuilder {

    override fun build(question: String, context: List<RelevantChunk>): String {
        if (context.isEmpty()) return question

        val sb = StringBuilder()
        sb.appendLine("Ответь на вопрос, основываясь на следующем контексте:")
        sb.appendLine()

        for ((index, relevant) in context.withIndex()) {
            val source = relevant.chunk.source
            sb.appendLine("[Источник: $source]")
            sb.appendLine(relevant.chunk.text)
            if (index < context.size - 1) sb.appendLine()
        }

        sb.appendLine()
        sb.appendLine("Вопрос: $question")
        sb.appendLine()
        sb.append("Если ответа нет в контексте, скажи \"У меня недостаточно информации.\"")

        return sb.toString()
    }
}
