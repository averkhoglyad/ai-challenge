package io.averkhogliad.ai.challenge.week2.unit.cli

import io.averkhogliad.ai.challenge.week2.cli.ConsoleCliRenderer
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода renderStatusDebug() в ConsoleCliRenderer.
 * US-DBG-5: Обновление :status
 */
class ConsoleCliRendererStatusDebugTest : FreeSpec({

    val renderer = ConsoleCliRenderer()

    "renderStatusDebug" - {

        "prints enabled when debug mode is on" {
            // Given
            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // When
            renderer.renderStatusDebug(true)

            // Then
            val output = outputStream.toString()
            output shouldContain "Debug mode: enabled"
            output shouldNotContain "Debug mode: disabled"

            // Cleanup
            System.setOut(System.out)
        }

        "prints disabled when debug mode is off" {
            // Given
            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // When
            renderer.renderStatusDebug(false)

            // Then
            val output = outputStream.toString()
            output shouldContain "Debug mode: disabled"
            output shouldNotContain "Debug mode: enabled"

            // Cleanup
            System.setOut(System.out)
        }

        "prints newlines for formatting" {
            // Given
            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // When
            renderer.renderStatusDebug(true)

            // Then
            val output = outputStream.toString()
            val lineSep = System.lineSeparator()
            (output.contains(lineSep + lineSep) || output.contains("\n\n")) shouldBe true

            // Cleanup
            System.setOut(System.out)
        }
    }
})
