package io.averkhogliad.ai.challenge.week3.cli.unit.cli

import io.averkhogliad.ai.challenge.week3.cli.cli.*

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода renderStatusDebug() в ConsoleCliRenderer.
 * US-DBG-5: Обновление :status
 */
class ConsoleCliRendererStatusDebugTest : FreeSpec({

    val renderer = ConsoleCliRenderer()

    "renderStatusDebug should print enabled when debug mode is on" {
        // Given
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        // When
        renderer.renderStatusDebug(true)

        // Then
        val output = outputStream.toString()
        output shouldContain "Debug mode: enabled"
        output.contains("Debug mode: disabled") shouldBe false

        // Cleanup
        System.setOut(System.out)
    }

    "renderStatusDebug should print disabled when debug mode is off" {
        // Given
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        // When
        renderer.renderStatusDebug(false)

        // Then
        val output = outputStream.toString()
        output shouldContain "Debug mode: disabled"
        output.contains("Debug mode: enabled") shouldBe false

        // Cleanup
        System.setOut(System.out)
    }

    "renderStatusDebug should print newlines for formatting" {
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
        (output.contains(lineSep + lineSep) || output.contains("\n\n")) shouldBe true

        // Cleanup
        System.setOut(System.out)
    }
})
