package io.averkhogliad.cli.repl.mordant.common

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MarkdownRendererTest {

    @Test
    fun `renders markdown to plain text`() {
        val terminal = Terminal(AnsiLevel.NONE)
        val renderer = MarkdownRenderer(terminal)

        val result = renderer.render("**bold** and *italic*")

        assertContains(result, "bold")
        assertContains(result, "italic")
        assertFalse(result.contains("**"), "Markdown syntax should be stripped: $result")
        assertFalse(result.contains("<"), "HTML tags should not be present: $result")
    }

    @Test
    fun `renders heading as plain text`() {
        val terminal = Terminal(AnsiLevel.NONE)
        val renderer = MarkdownRenderer(terminal)

        val result = renderer.render("# Heading")

        assertContains(result, "Heading")
        assertFalse(result.contains("#"), "Markdown heading syntax should be stripped: $result")
    }
}
