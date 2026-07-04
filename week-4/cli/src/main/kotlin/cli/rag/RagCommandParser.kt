package io.averkhogliad.ai.challenge.week4.cli.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode

/**
 * Парсер RAG-команд.
 *
 * Чистая функция без побочных эффектов.
 * Распознаёт команды:
 * - `:rag`, `:rag status`, `:rag list` (Task 2)
 * - `:rag mode <mode>`, `:rag threshold <value>`, `:rag topk <i> <f>`, `:rag config` (Task 3)
 * - `:rag history [N]`, `:rag history --detail <id>`, `:rag history --clear`
 * - `:rag analyze`, `:rag analyze --compare <m1> <m2>`
 */
object RagCommandParser {

    /**
     * Парсит строку вида `:rag [subcommand] [args]` в [RagCommand].
     *
     * @param input полная строка ввода (включая `:rag `)
     * @return [RagCommand] или null, если команда не распознана
     */
    fun parse(input: String): RagCommand? {
        val trimmed = input.trim()
        if (!trimmed.startsWith(":rag")) return null

        val afterPrefix = trimmed.removePrefix(":rag").trim()

        // Пустая команда → toggle
        if (afterPrefix.isEmpty()) return RagCommand.Toggle

        val parts = afterPrefix.split("\\s+".toRegex())
        val subcommand = parts[0]
        val args = parts.drop(1)

        return when (subcommand) {
            "status" -> RagCommand.Status
            "list" -> RagCommand.List
            "config" -> RagCommand.Config
            "analyze" -> parseAnalyze(args)
            "mode" -> parseSetMode(args)
            "threshold" -> parseSetThreshold(args)
            "topk" -> parseSetTopK(args)
            "history" -> parseHistory(args)
            else -> null
        }
    }

    private fun parseSetMode(args: List<String>): RagCommand? {
        if (args.isEmpty()) return null
        val mode = parseSearchMode(args[0]) ?: return null
        return RagCommand.SetMode(mode)
    }

    private fun parseSetThreshold(args: List<String>): RagCommand? {
        if (args.isEmpty()) return null
        val value = args[0].toFloatOrNull() ?: return null
        if (value < 0f || value > 1f) return null
        return RagCommand.SetThreshold(value)
    }

    private fun parseSetTopK(args: List<String>): RagCommand? {
        if (args.size < 2) return null
        val initial = args[0].toIntOrNull() ?: return null
        val final = args[1].toIntOrNull() ?: return null
        if (initial <= 0 || final <= 0 || final > initial) return null
        return RagCommand.SetTopK(initial, final)
    }

    private fun parseHistory(args: List<String>): RagCommand? {
        if (args.isEmpty()) return RagCommand.History(limit = 10)

        return when {
            args[0] == "--clear" -> RagCommand.HistoryClear
            args[0] == "--detail" -> {
                if (args.size < 2) return null
                val id = args[1].toLongOrNull() ?: return null
                RagCommand.HistoryDetail(id)
            }

            else -> {
                val limit = args[0].toIntOrNull() ?: return null
                if (limit <= 0) return null
                RagCommand.History(limit)
            }
        }
    }

    private fun parseAnalyze(args: List<String>): RagCommand? {
        if (args.isEmpty()) return RagCommand.Analyze

        return when {
            args[0] == "--compare" -> {
                if (args.size < 3) return null
                val mode1 = parseSearchMode(args[1]) ?: return null
                val mode2 = parseSearchMode(args[2]) ?: return null
                RagCommand.Compare(mode1, mode2)
            }

            else -> null
        }
    }

    fun parseSearchMode(input: String): SearchMode? = when (input.lowercase()) {
        "raw" -> SearchMode.Raw
        "filtered" -> SearchMode.Filtered
        "reranked" -> SearchMode.Reranked
        "rewrite" -> SearchMode.Rewrite
        else -> null
    }
}
