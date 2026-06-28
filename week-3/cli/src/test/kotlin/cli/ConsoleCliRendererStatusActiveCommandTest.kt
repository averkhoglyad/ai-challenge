package io.averkhogliad.ai.challenge.week3.cli.cli

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода renderStatusActiveCommand в ConsoleCliRenderer.
 * US-STATUS-1: Отображение активной FSM-команды в выводе :status.
 */
class ConsoleCliRendererStatusActiveCommandTest {

    private val renderer = ConsoleCliRenderer()

    /**
     * Вспомогательный метод для захвата вывода в консоль.
     */
    private fun captureOutput(block: () -> Unit): String {
        val outputStream = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(outputStream))
        try {
            block()
        } finally {
            System.setOut(originalOut)
        }
        return outputStream.toString().trim()
    }

    @Test
    fun `renderStatusActiveCommand with active command name displays command name`() {
        val output = captureOutput {
            renderer.renderStatusActiveCommand("plan")
        }

        assertTrue(output.contains("Активная команда: plan"))
    }

    @Test
    fun `renderStatusActiveCommand with null displays none`() {
        val output = captureOutput {
            renderer.renderStatusActiveCommand(null)
        }

        assertTrue(output.contains("Активная команда: нет"))
    }

    @Test
    fun `renderStatusActiveCommand with describe command displays describe`() {
        val output = captureOutput {
            renderer.renderStatusActiveCommand("describe")
        }

        assertTrue(output.contains("Активная команда: describe"))
    }

    @Test
    fun `renderStatusActiveCommand output is not empty`() {
        val output = captureOutput {
            renderer.renderStatusActiveCommand("test")
        }

        assertTrue(output.isNotEmpty())
    }
}
