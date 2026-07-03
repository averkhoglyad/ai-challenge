package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.ConsoleCliRenderer
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода [ConsoleCliRenderer.waitForEnter].
 * Проверяет корректность вывода подсказки и блокировки до нажатия Enter.
 */
class ConsoleCliRendererWaitForEnterTest : FreeSpec({

    "waitForEnter" - {
        "should print prompt and wait for input" {
            // given
            val renderer = ConsoleCliRenderer()
            val originalIn = System.`in`
            val originalOut = System.out

            try {
                val input = ByteArrayInputStream("\n".toByteArray())
                System.setIn(input)

                val outputStream = ByteArrayOutputStream()
                System.setOut(PrintStream(outputStream))

                // when
                renderer.waitForEnter()

                // then
                val output = outputStream.toString()
                output shouldContain "Нажмите Enter для продолжения"
            } finally {
                System.setIn(originalIn)
                System.setOut(originalOut)
            }
        }

        "should handle EOF gracefully" {
            // given
            val renderer = ConsoleCliRenderer()
            val originalIn = System.`in`
            val originalOut = System.out

            try {
                val input = ByteArrayInputStream(ByteArray(0))
                System.setIn(input)

                val outputStream = ByteArrayOutputStream()
                System.setOut(PrintStream(outputStream))

                // when & then
                shouldNotThrow<Throwable> {
                    renderer.waitForEnter()
                }
            } finally {
                System.setIn(originalIn)
                System.setOut(originalOut)
            }
        }

        "should flush output before waiting" {
            // given
            val renderer = ConsoleCliRenderer()
            val originalIn = System.`in`
            val originalOut = System.out

            try {
                val input = ByteArrayInputStream(ByteArray(0))
                System.setIn(input)

                val outputStream = ByteArrayOutputStream()
                System.setOut(PrintStream(outputStream))

                // when
                renderer.waitForEnter()

                // then
                val output = outputStream.toString()
                output.isNotEmpty() shouldBe true
            } finally {
                System.setIn(originalIn)
                System.setOut(originalOut)
            }
        }
    }
})
