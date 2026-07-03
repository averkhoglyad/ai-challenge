package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.ConsoleCliRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.CommandState
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

    "Basic output" - {
        "renderFsmState should output command name" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 1,
                expectedAction = "Check task"
            )

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output shouldContain "[DEBUG] Команда: plan"
        }

        "renderFsmState should output current stage" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.EXECUTION,
                currentStep = 1,
                expectedAction = "Send request"
            )

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output shouldContain "[DEBUG] Этап: EXECUTION"
        }

        "renderFsmState should output current step" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.VALIDATION,
                currentStep = 3,
                expectedAction = "Wait confirmation"
            )

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output shouldContain "[DEBUG] Шаг: 3"
        }
    }

    "Action output" - {
        "renderFsmState should output expected action when not empty" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 1,
                expectedAction = "Check task exists"
            )

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output shouldContain "[DEBUG] Действие: Check task exists"
        }

        "renderFsmState should not output action when empty" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 1,
                expectedAction = ""
            )

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output.contains("[DEBUG] Действие:") shouldBe false
        }
    }

    "Context output" - {
        "renderFsmState should output context when not empty" {
            // given
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

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output shouldContain "[DEBUG] Контекст:"
            output shouldContain "taskId: 123"
            output shouldContain "description: Test task"
        }

        "renderFsmState should not output context when empty" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.PLANNING,
                currentStep = 1,
                expectedAction = "Check task",
                context = emptyMap()
            )

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output.contains("[DEBUG] Контекст:") shouldBe false
        }
    }

    "Edge cases" - {
        "renderFsmState should output all stages correctly" {
            // given
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

                // when
                val output = captureOutput { renderer.renderFsmState(state) }

                // then
                output shouldContain "[DEBUG] Этап: $stage"
            }
        }

        "renderFsmState should handle different command names" {
            // given
            val commandNames = listOf("plan", "describe", "edit", "custom-command")

            commandNames.forEach { commandName ->
                val state = CommandState(
                    commandName = commandName,
                    currentStage = CommandStage.PLANNING,
                    currentStep = 1,
                    expectedAction = "Test"
                )

                // when
                val output = captureOutput { renderer.renderFsmState(state) }

                // then
                output shouldContain "[DEBUG] Команда: $commandName"
            }
        }

        "renderFsmState should handle large step numbers" {
            // given
            val state = CommandState(
                commandName = "plan",
                currentStage = CommandStage.EXECUTION,
                currentStep = 999,
                expectedAction = "Processing"
            )

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output shouldContain "[DEBUG] Шаг: 999"
        }

        "renderFsmState should handle context with special characters" {
            // given
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

            // when
            val output = captureOutput { renderer.renderFsmState(state) }

            // then
            output shouldContain "key-with-dash: value with spaces"
            output shouldContain "key_with_underscore: value@with#special\$chars"
        }
    }
})
