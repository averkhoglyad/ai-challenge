package io.averkhogliad.ai.challenge.week3.cli.unit.cli

import io.averkhogliad.ai.challenge.week3.cli.cli.*

import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandState
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для визуализации FSM в debug-режиме (US-DBG-3).
 *
 * Проверяют корректность вывода состояния FSM через метод renderFsmState().
 */
class ConsoleCliRendererFsmTest : FreeSpec({

    val renderer = ConsoleCliRenderer()

    /**
     * Вспомогательный метод для захвата вывода в System.out.
     */
    fun captureOutput(block: () -> Unit): String {
        val outputStream = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(outputStream))
        try {
            block()
            System.out.flush()
            return outputStream.toString().trim()
        } finally {
            System.setOut(originalOut)
        }
    }

    "renderFsmState should output command name" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = "Check task"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output shouldContain "[DEBUG] Команда: plan"
    }

    "renderFsmState should output current stage" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.EXECUTION,
            currentStep = 1,
            expectedAction = "Send request"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output shouldContain "[DEBUG] Этап: EXECUTION"
    }

    "renderFsmState should output current step" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.VALIDATION,
            currentStep = 3,
            expectedAction = "Wait confirmation"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output shouldContain "[DEBUG] Шаг: 3"
    }

    "renderFsmState should output expected action when not empty" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = "Check task exists"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output shouldContain "[DEBUG] Действие: Check task exists"
    }

    "renderFsmState should not output action when empty" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = ""
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output.contains("[DEBUG] Действие:") shouldBe false
    }

    "renderFsmState should output context when not empty" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.EXECUTION,
            currentStep = 2,
            expectedAction = "Parse response",
            context = mapOf(
                "taskId" to "123",
                "description" to "Test task"
            )
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output shouldContain "[DEBUG] Контекст:"
        output shouldContain "taskId: 123"
        output shouldContain "description: Test task"
    }

    "renderFsmState should not output context when empty" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = "Check task",
            context = emptyMap()
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output.contains("[DEBUG] Контекст:") shouldBe false
    }

    "renderFsmState should output all stages correctly" {
        val stages = listOf(
            CommandStage.PLANNING,
            CommandStage.EXECUTION,
            CommandStage.VALIDATION,
            CommandStage.DONE
        )

        stages.forEach { stage ->
            val state = CommandState(
                commandName = "plan",
                currentStage = stage,
                currentStep = 1,
                expectedAction = "Test"
            )

            val output = captureOutput { renderer.renderFsmState(state) }

            output shouldContain "[DEBUG] Этап: $stage"
        }
    }

    "renderFsmState should handle different command names" {
        val commandNames = listOf("plan", "describe", "edit", "custom-command")

        commandNames.forEach { commandName ->
            val state = CommandState(
                commandName = commandName,
                currentStage = CommandStage.PLANNING,
                currentStep = 1,
                expectedAction = "Test"
            )

            val output = captureOutput { renderer.renderFsmState(state) }

            output shouldContain "[DEBUG] Команда: $commandName"
        }
    }

    "renderFsmState should handle large step numbers" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.EXECUTION,
            currentStep = 999,
            expectedAction = "Processing"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output shouldContain "[DEBUG] Шаг: 999"
    }

    "renderFsmState should handle context with special characters" {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.EXECUTION,
            currentStep = 1,
            expectedAction = "Test",
            context = mapOf(
                "key-with-dash" to "value with spaces",
                "key_with_underscore" to "value@with#special\$chars"
            )
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        output shouldContain "key-with-dash: value with spaces"
        output shouldContain "key_with_underscore: value@with#special\$chars"
    }
})
