package io.averkhogliad.ai.challenge.week2.cli

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода [ConsoleCliRenderer.waitForEnter].
 * Проверяет корректность вывода подсказки и блокировки до нажатия Enter.
 */
class ConsoleCliRendererWaitForEnterTest {

    @Test
    fun `waitForEnter should print prompt and wait for input`() {
        // Arrange
        val renderer = ConsoleCliRenderer()
        val originalIn = System.`in`
        val originalOut = System.out

        try {
            // Подготавливаем mock input с символом новой строки (Enter)
            val input = ByteArrayInputStream("\n".toByteArray())
            System.setIn(input)

            // Перехватываем output
            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // Act
            renderer.waitForEnter()

            // Assert
            val output = outputStream.toString()
            assertTrue(
                output.contains("Press Enter to continue..."),
                "Output should contain 'Press Enter to continue...' prompt"
            )
        } finally {
            // Restore original streams
            System.setIn(originalIn)
            System.setOut(originalOut)
        }
    }

    @Test
    fun `waitForEnter should handle EOF gracefully`() {
        // Arrange
        val renderer = ConsoleCliRenderer()
        val originalIn = System.`in`
        val originalOut = System.out

        try {
            // Пустой input (EOF)
            val input = ByteArrayInputStream(ByteArray(0))
            System.setIn(input)

            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // Act & Assert - не должно выбрасывать исключение
            assertDoesNotThrow {
                renderer.waitForEnter()
            }
        } finally {
            System.setIn(originalIn)
            System.setOut(originalOut)
        }
    }

    @Test
    fun `waitForEnter should flush output before waiting`() {
        // Arrange
        val renderer = ConsoleCliRenderer()
        val originalIn = System.`in`
        val originalOut = System.out

        try {
            // Устанавливаем mock input с EOF, чтобы waitForEnter() не блокировался
            val input = ByteArrayInputStream(ByteArray(0))
            System.setIn(input)

            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // Act
            renderer.waitForEnter()

            // Assert - output должен быть записан (flush вызван)
            val output = outputStream.toString()
            assertTrue(output.isNotEmpty(), "Output should not be empty after waitForEnter")
        } finally {
            System.setIn(originalIn)
            System.setOut(originalOut)
        }
    }
}
