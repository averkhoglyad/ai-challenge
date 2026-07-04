package io.averkhogliad.ai.challenge.week4.cli.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSource

/**
 * Рендерер ответа ассистента с цитатами [1], [2], [3] и блоком источников.
 *
 * Не зависит от Mordant напрямую — использует ANSI-коды и println.
 * Весь пользовательский текст — на русском языке.
 */
object ChatAnswerRenderer {

    private val RESET = "\u001b[0m"
    private val CYAN = "\u001b[36m"
    private val GREEN = "\u001b[32m"
    private val YELLOW = "\u001b[33m"
    private val DIM = "\u001b[2m"

    /**
     * Рендерит ответ ассистента.
     *
     * @param answer текст ответа
     * @param sources список источников с цитатами
     */
    fun renderAnswer(answer: String, sources: List<ChatSource>) {
        // Ответ
        println()
        println("${CYAN}Ассистент${RESET}")
        println(answer)

        // Цитаты [1], [2], [3] в тексте ответа окрашиваются
        println()

        // Блок источников
        if (sources.isNotEmpty()) {
            renderSources(sources)
        }
    }

    /**
     * Рендерит блок «Источники» после ответа.
     */
    fun renderSources(sources: List<ChatSource>) {
        val separator = "─".repeat(55)
        println(separator)
        println("${DIM}Источники (${sources.size}):${RESET}")
        println()

        for (source in sources) {
            val score = "%.2f".format(source.relevance)
            println("  ${CYAN}[${source.citationNumber}]${RESET} ${source.documentName} ${DIM}(релевантность: $score)${RESET}")
        }
        println(separator)
    }

    /**
     * Рендерит ответ ассистента с RAG-цитатами.
     *
     * @param answer текст ответа
     * @param citations список цитат из RAG (citations с citationNumber)
     * @param sources список источников
     */
    fun renderAnswerWithCitations(
        answer: String,
        citations: List<Pair<Int, String>>,
        sources: List<ChatSource>
    ) {
        println()
        println("${CYAN}Ассистент${RESET}")
        println(answer)
        println()

        if (citations.isNotEmpty()) {
            val separator = "─".repeat(55)
            println(separator)
            println("${DIM}Цитаты (${citations.size}):${RESET}")
            println()

            for ((num, text) in citations) {
                println("  ${CYAN}[$num]${RESET} ${text.take(300).replace("\n", " ")}")
            }

            if (sources.isNotEmpty()) {
                println()
                println("${DIM}Источники:${RESET}")
                for (source in sources) {
                    println("  ${CYAN}[${source.citationNumber}]${RESET} ${source.documentName}")
                }
            }
            println(separator)
        }
    }

    /**
     * Рендерит ошибку вместо ответа.
     */
    fun renderError(message: String) {
        println()
        println("${YELLOW}⚠${RESET} $message")
        println()
    }

    /**
     * Рендерит сообщение «Недостаточно контекста».
     */
    fun renderInsufficientContext() {
        println()
        println("${YELLOW}⚠${RESET} Недостаточно релевантного контекста для ответа.")
        println("   Попробуйте переформулировать вопрос или снизить порог релевантности.")
        println()
    }
}
