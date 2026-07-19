package io.averkhogliad.ai.challenge.week6.cli.rendering

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.SearchHit
import io.averkhogliad.cli.repl.mordant.common.TableRenderer

class SearchResultsRenderer(
    terminal: Terminal,
) : TableRenderer<List<SearchHit>>(terminal) {

    override fun headers(): List<String> = listOf("File", "Line", "Snippet")

    override fun rows(data: List<SearchHit>): List<List<String>> = data.map { hit ->
        listOf(
            hit.path.toString(),
            hit.line.toString(),
            hit.snippet.take(80),
        )
    }

    override fun render(data: List<SearchHit>): String {
        if (data.isEmpty()) {
            return terminal.render(TextColors.yellow("No results found."))
        }
        return super.render(data)
    }
}
