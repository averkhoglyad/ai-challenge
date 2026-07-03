package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.ConsoleCliRenderer
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для метода renderStatusActiveCommand в ConsoleCliRenderer.
 * US-STATUS-1: Отображение активной FSM-команды в выводе :status.
 */
class ConsoleCliRendererStatusActiveCommandTest : FreeSpec({

    val renderer = ConsoleCliRenderer()

    /**
     * Вспомогательный метод для захвата вывода в консоль.
     */
    fun captureOutput(block: () -> Unit): String {
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

    "renderStatusActiveCommand" - {
        "with active command name displays command name" {
            // when
            val output = captureOutput {
                renderer.renderStatusActiveCommand("plan")
            }

            // then
            output shouldContain "Активная команда: plan"
        }

        "with null displays none" {
            // when
            val output = captureOutput {
                renderer.renderStatusActiveCommand(null)
            }

            // then
            output shouldContain "Активная команда: нет"
        }

        "with describe command displays describe" {
            // when
            val output = captureOutput {
                renderer.renderStatusActiveCommand("describe")
            }

            // then
            output shouldContain "Активная команда: describe"
        }

        "output is not empty" {
            // when
            val output = captureOutput {
                renderer.renderStatusActiveCommand("test")
            }

            // then
            output.isEmpty() shouldBe false
        }
    }
})
