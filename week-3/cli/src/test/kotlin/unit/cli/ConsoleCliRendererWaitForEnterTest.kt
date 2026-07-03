package io.averkhogliad.ai.challenge.week3.cli.unit.cli

import io.averkhogliad.ai.challenge.week3.cli.cli.*

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода [ConsoleCliRenderer.waitForEnter].
 * Проверяет корректность вывода подсказки и блокировки до нажатия Enter.
 */
class ConsoleCliRendererWaitForEnterTest : FreeSpec({

    "waitForEnter should print prompt and wait for input" {
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
            output shouldContain "Нажмите Enter для продолжения"
        } finally {
            // Restore original streams
            System.setIn(originalIn)
            System.setOut(originalOut)
        }
    }

    "waitForEnter should handle EOF gracefully" {
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
            shouldNotThrow<Throwable> {
                renderer.waitForEnter()
            }
        } finally {
            System.setIn(originalIn)
            System.setOut(originalOut)
        }
    }

    "waitForEnter should flush output before waiting" {
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
            output.isNotEmpty() shouldBe true
        } finally {
            System.setIn(originalIn)
            System.setOut(originalOut)
        }
    }
})
