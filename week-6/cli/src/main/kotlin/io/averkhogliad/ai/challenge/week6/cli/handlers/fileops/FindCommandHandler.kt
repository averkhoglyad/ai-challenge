package io.averkhogliad.ai.challenge.week6.cli.handlers.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.FileSearchUseCase
import io.averkhogliad.ai.challenge.week6.cli.rendering.SearchResultsRenderer
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class FindCommandHandler(
    private val fileSearchUseCase: FileSearchUseCase,
    private val renderer: SearchResultsRenderer,
) : CommandHandler {

    override val name: String = "/find"
    override val aliases: List<String> = listOf("/search")
    override val description: String = "Поиск по файлам проекта: /find <query> [--in <dir>] [--ext <ext>] [--case]"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/find" || rawInput.startsWith("/find ") ||
                rawInput == "/search" || rawInput.startsWith("/search ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val input = rawInput.removePrefix("/find").removePrefix("/search").trim()

        if (input.isEmpty()) {
            return CommandEffect.Print(
                "Использование: /find <query> [--in <dir>] [--ext <ext>] [--case]",
                isError = true
            )
        }

        val args = parseArgs(input)
        val query = args.firstOrNull() ?: return CommandEffect.Print("Укажите поисковый запрос", isError = true)

        val inIdx = args.indexOf("--in")
        val inDir = if (inIdx >= 0 && inIdx + 1 < args.size) args[inIdx + 1] else null
        val extIdx = args.indexOf("--ext")
        val ext = if (extIdx >= 0 && extIdx + 1 < args.size) args[extIdx + 1] else null
        val caseSensitive = args.contains("--case")

        return when (val result = fileSearchUseCase.execute(query, ext, inDir, caseSensitive)) {
            is DomainResult.Success -> {
                val hits = result.value
                if (hits.isEmpty()) {
                    CommandEffect.Print("Ничего не найдено по запросу: $query")
                } else {
                    val total = if (hits.size >= 50) " (показаны первые 50)" else ""
                    CommandEffect.Print(
                        "Результаты поиска \"$query\" (${hits.size} совпадений)$total:\n\n" + renderer.render(hits)
                    )
                }
            }

            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }

    private fun parseArgs(input: String): List<String> {
        val parts = mutableListOf<String>()
        var i = 0
        val chars = input.toCharArray()
        val current = StringBuilder()

        var inQuote = false
        while (i < chars.size) {
            when {
                chars[i] == '"' -> inQuote = !inQuote
                chars[i] == ' ' && !inQuote -> {
                    if (current.isNotEmpty()) {
                        parts.add(current.toString())
                        current.clear()
                    }
                }

                else -> current.append(chars[i])
            }
            i++
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        // Unclosed quote: already handled — remaining text was collected in `current`

        return parts
    }
}
