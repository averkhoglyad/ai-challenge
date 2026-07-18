package io.averkhogliad.cli.repl.mordant.common

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.cli.repl.mordant.rendering.MordantRenderer

class MarkdownRenderer(
    terminal: Terminal
) : MordantRenderer<String>(terminal) {

    override fun render(data: String): String {
        val markdown = Markdown(data)
        return terminal.render(markdown)
    }
}
