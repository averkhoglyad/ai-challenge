package io.averkhogliad.ai.challenge.week3.cli.unit.cli

import io.averkhogliad.ai.challenge.week3.cli.cli.*

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
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

    "renderStatusActiveCommand with active command name displays command name" {
        val output = captureOutput {
            renderer.renderStatusActiveCommand("plan")
        }

        output shouldContain "Активная команда: plan"
    }

    "renderStatusActiveCommand with null displays none" {
        val output = captureOutput {
            renderer.renderStatusActiveCommand(null)
        }

        output shouldContain "Активная команда: нет"
    }

    "renderStatusActiveCommand with describe command displays describe" {
        val output = captureOutput {
            renderer.renderStatusActiveCommand("describe")
        }

        output shouldContain "Активная команда: describe"
    }

    "renderStatusActiveCommand output is not empty" {
        val output = captureOutput {
            renderer.renderStatusActiveCommand("test")
        }

        output.isEmpty() shouldBe false
    }
})
