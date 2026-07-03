package io.averkhogliad.ai.challenge.week4.cli.cli.rag

/**
 * Парсер RAG-команд.
 *
 * Чистая функция без побочных эффектов.
 * Распознаёт: `:rag`, `:rag status`, `:rag list`.
 */
object RagCommandParser {

    /**
     * Парсит строку вида `:rag [subcommand]` в [RagCommand].
     *
     * @param input полная строка ввода (включая `:rag `)
     * @return [RagCommand] или null, если команда не распознана
     */
    fun parse(input: String): RagCommand? {
        val trimmed = input.trim()
        if (!trimmed.startsWith(":rag")) return null

        val afterPrefix = trimmed.removePrefix(":rag").trim()

        return when {
            afterPrefix.isEmpty() -> RagCommand.Toggle
            afterPrefix == "status" -> RagCommand.Status
            afterPrefix == "list" -> RagCommand.List
            else -> null
        }
    }
}
