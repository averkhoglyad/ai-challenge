package io.averkhogliad.cli.repl.mordant.writer

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.cli.repl.core.OutputWriter

class MordantOutputWriter(
    private val terminal: Terminal
) : OutputWriter {

    override suspend fun write(text: String) {
        terminal.println(text)
    }

    override suspend fun writeError(text: String) {
        terminal.println(TextColors.red(text))
    }

    override suspend fun writePrompt(prompt: String) {
        terminal.print(TextColors.cyan(prompt))
    }
}
