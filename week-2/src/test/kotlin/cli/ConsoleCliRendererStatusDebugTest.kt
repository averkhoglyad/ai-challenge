package io.averkhogliad.ai.challenge.week2.cli

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода renderStatusDebug() в ConsoleCliRenderer.
 * US-DBG-5: Обновление :status
 */
class ConsoleCliRendererStatusDebugTest {

    private val renderer = ConsoleCliRenderer()

    @Test
    fun `renderStatusDebug should print enabled when debug mode is on`() {
        // Given
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        // When
        renderer.renderStatusDebug(true)

        // Then
        val output = outputStream.toString()
        assertTrue(output.contains("Debug mode: enabled"))
        assertFalse(output.contains("Debug mode: disabled"))

        // Cleanup
        System.setOut(System.out)
    }

    @Test
    fun `renderStatusDebug should print disabled when debug mode is off`() {
        // Given
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        // When
        renderer.renderStatusDebug(false)

        // Then
        val output = outputStream.toString()
        assertTrue(output.contains("Debug mode: disabled"))
        assertFalse(output.contains("Debug mode: enabled"))

        // Cleanup
        System.setOut(System.out)
    }

    @Test
    fun `renderStatusDebug should print newlines for formatting`() {
        // Given
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        // When
        renderer.renderStatusDebug(true)

        // Then
        val output = outputStream.toString()
        // Проверяем наличие пустых строк для форматирования
        // Используем lineSeparator() для платформенной независимости
        val lineSep = System.lineSeparator()
        assertTrue(output.contains(lineSep + lineSep) || output.contains("\n\n"))

        // Cleanup
        System.setOut(System.out)
    }
}
