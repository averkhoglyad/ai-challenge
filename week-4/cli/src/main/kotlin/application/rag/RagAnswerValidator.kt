package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagAnswer

class RagAnswerValidator(private val config: RagConfig) {

    fun validate(answer: RagAnswer): List<String> {
        if (answer.isInsufficientContext) return emptyList()

        val errors = mutableListOf<String>()

        if (answer.citations.isEmpty()) {
            errors.add("Ответ не содержит цитат")
        }

        if (answer.citations.size < config.minCitationsRequired) {
            errors.add("Недостаточно цитат: ${answer.citations.size} < ${config.minCitationsRequired}")
        }

        val usedInText = answer.citations.indices.any { idx ->
            answer.answer.contains("[${idx + 1}]")
        }
        if (!usedInText && answer.citations.isNotEmpty()) {
            errors.add("В тексте ответа нет ссылок на цитаты")
        }

        return errors
    }
}
