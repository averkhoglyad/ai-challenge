package io.averkhogliad.cli.repl.mordant.writer

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class MordantOutputWriterTest {

    @Test
    fun `write outputs plain text`() = runTest {
        val captured = captureOutput(AnsiLevel.NONE) { writer ->
            writer.write("Hello")
        }
        assertContains(captured, "Hello")
    }

    @Test
    fun `writeError outputs red text`() = runTest {
        val captured = captureOutput(AnsiLevel.TRUECOLOR) { writer ->
            writer.writeError("Error message")
        }
        assertContains(captured, "Error message")
        assertTrue(captured.contains("\u001b[31m"), "Expected red ANSI code in: $captured")
    }

    @Test
    fun `writePrompt outputs cyan text without newline`() = runTest {
        val captured = captureOutput(AnsiLevel.TRUECOLOR) { writer ->
            writer.writePrompt("> ")
        }
        assertContains(captured, "> ")
        assertTrue(captured.contains("\u001b[36m"), "Expected cyan ANSI code in: $captured")
        assertTrue(
            !captured.endsWith("\n") && !captured.endsWith("\r\n"),
            "Prompt should not end with newline: '$captured'"
        )
    }

    private suspend fun captureOutput(
        ansiLevel: AnsiLevel,
        action: suspend (MordantOutputWriter) -> Unit
    ): String {
        val baos = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(baos, true))
        try {
            val terminal = Terminal(ansiLevel)
            val writer = MordantOutputWriter(terminal)
            action(writer)
        } finally {
            System.setOut(originalOut)
        }
        return baos.toString()
    }
}
