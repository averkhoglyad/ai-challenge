package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagAnswerValidator
import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.Citation
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagAnswer
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize

class RagAnswerValidatorTest : FreeSpec({

    val config = RagConfig(minCitationsRequired = 1, maxCitationsPerAnswer = 5)
    val validator = RagAnswerValidator(config)

    fun citation(index: Int = 1): Citation = Citation(
        chunkId = "chunk-$index",
        text = "text $index",
        source = "doc.md",
        relevanceScore = 0.9f
    )

    "validate" - {

        "returns empty errors for valid answer with citations and link" {
            val answer = RagAnswer(
                answer = "Paris is the capital [1]",
                citations = listOf(citation(1)),
                ragEnabled = true
            )
            validator.validate(answer).shouldBeEmpty()
        }

        "returns empty errors for valid answer with multiple citations" {
            val answer = RagAnswer(
                answer = "Fact one [1] and fact two [2]",
                citations = listOf(citation(1), citation(2)),
                ragEnabled = true
            )
            validator.validate(answer).shouldBeEmpty()
        }

        "returns empty errors for InsufficientContext answer" {
            val answer = RagAnswer(
                answer = "not enough context",
                isInsufficientContext = true,
                ragEnabled = true
            )
            validator.validate(answer).shouldBeEmpty()
        }

        "returns error when no citations" {
            val answer = RagAnswer(
                answer = "Some answer without citations",
                citations = emptyList(),
                ragEnabled = true
            )
            val errors = validator.validate(answer)
            errors shouldContain "Ответ не содержит цитат"
        }

        "returns error when citations count < minCitationsRequired" {
            val strictConfig = RagConfig(minCitationsRequired = 3)
            val strictValidator = RagAnswerValidator(strictConfig)
            val answer = RagAnswer(
                answer = "Answer [1]",
                citations = listOf(citation(1)),
                ragEnabled = true
            )
            val errors = strictValidator.validate(answer)
            errors shouldContain "Недостаточно цитат: 1 < 3"
        }

        "returns error when no citation links in text" {
            val answer = RagAnswer(
                answer = "Answer without any citation links",
                citations = listOf(citation(1)),
                ragEnabled = true
            )
            val errors = validator.validate(answer)
            errors shouldContain "В тексте ответа нет ссылок на цитаты"
        }

        "returns error when link number does not match any citation index" {
            val answer = RagAnswer(
                answer = "Answer with wrong link [5]",
                citations = listOf(citation(1), citation(2)),
                ragEnabled = true
            )
            val errors = validator.validate(answer)
            errors shouldContain "В тексте ответа нет ссылок на цитаты"
        }

        "multiple errors can be returned together" {
            val strictConfig = RagConfig(minCitationsRequired = 2)
            val strictValidator = RagAnswerValidator(strictConfig)
            val answer = RagAnswer(
                answer = "Answer without links",
                citations = listOf(citation(1)),
                ragEnabled = true
            )
            val errors = strictValidator.validate(answer)
            errors shouldHaveSize 2
        }
    }
})
