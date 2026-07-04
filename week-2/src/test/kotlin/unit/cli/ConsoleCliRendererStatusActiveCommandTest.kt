package io.averkhogliad.ai.challenge.week2.unit.cli

import io.averkhogliad.ai.challenge.week2.cli.ConsoleCliRenderer
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
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

        "displays command name when active command name provided" {
            val output = captureOutput {
                renderer.renderStatusActiveCommand("plan")
            }

            output shouldContain "Активная команда: plan"
        }

        "displays none when command name is null" {
            val output = captureOutput {
                renderer.renderStatusActiveCommand(null)
            }

            output shouldContain "Активная команда: нет"
        }

        "displays describe command name" {
            val output = captureOutput {
                renderer.renderStatusActiveCommand("describe")
            }

            output shouldContain "Активная команда: describe"
        }

        "produces non-empty output" {
            val output = captureOutput {
                renderer.renderStatusActiveCommand("test")
            }

            output.shouldNotBeEmpty()
        }
    }
})
