package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.ConsoleCliRenderer
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода renderStatusDebug() в ConsoleCliRenderer.
 * US-DBG-5: Обновление :status
 */
class ConsoleCliRendererStatusDebugTest : FreeSpec({

    val renderer = ConsoleCliRenderer()

    "renderStatusDebug" - {
        "should print enabled when debug mode is on" {
            // given
            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // when
            renderer.renderStatusDebug(true)

            // then
            val output = outputStream.toString()
            output shouldContain "Debug mode: enabled"
            output.contains("Debug mode: disabled") shouldBe false

            // cleanup
            System.setOut(System.out)
        }

        "should print disabled when debug mode is off" {
            // given
            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // when
            renderer.renderStatusDebug(false)

            // then
            val output = outputStream.toString()
            output shouldContain "Debug mode: disabled"
            output.contains("Debug mode: enabled") shouldBe false

            // cleanup
            System.setOut(System.out)
        }

        "should print newlines for formatting" {
            // given
            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            // when
            renderer.renderStatusDebug(true)

            // then
            val output = outputStream.toString()
            val lineSep = System.lineSeparator()
            (output.contains(lineSep + lineSep) || output.contains("\n\n")) shouldBe true

            // cleanup
            System.setOut(System.out)
        }
    }
})
