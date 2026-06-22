package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week2.domain.model.CommandState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Unit-тесты для визуализации FSM в debug-режиме (US-DBG-3).
 *
 * Проверяют корректность вывода состояния FSM через метод renderFsmState().
 */
class ConsoleCliRendererFsmTest {

    private val renderer = ConsoleCliRenderer()

    /**
     * Вспомогательный метод для захвата вывода в System.out.
     */
    private fun captureOutput(block: () -> Unit): String {
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

    @Test
    fun `renderFsmState should output command name`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = "Check task"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        assertTrue(output.contains("[DEBUG] Command: plan"))
    }

    @Test
    fun `renderFsmState should output current stage`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.EXECUTION,
            currentStep = 1,
            expectedAction = "Send request"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        assertTrue(output.contains("[DEBUG] Stage: EXECUTION"))
    }

    @Test
    fun `renderFsmState should output current step`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.VALIDATION,
            currentStep = 3,
            expectedAction = "Wait confirmation"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        assertTrue(output.contains("[DEBUG] Step: 3"))
    }

    @Test
    fun `renderFsmState should output expected action when not empty`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = "Check task exists"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        assertTrue(output.contains("[DEBUG] Action: Check task exists"))
    }

    @Test
    fun `renderFsmState should not output action when empty`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = ""
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        assertFalse(output.contains("[DEBUG] Action:"))
    }

    @Test
    fun `renderFsmState should output context when not empty`() {
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

        assertTrue(output.contains("[DEBUG] Context:"))
        assertTrue(output.contains("taskId: 123"))
        assertTrue(output.contains("description: Test task"))
    }

    @Test
    fun `renderFsmState should not output context when empty`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = "Check task",
            context = emptyMap()
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        assertFalse(output.contains("[DEBUG] Context:"))
    }

    @Test
    fun `renderFsmState should output all stages correctly`() {
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

            assertTrue(output.contains("[DEBUG] Stage: $stage"))
        }
    }

    @Test
    fun `renderFsmState should handle different command names`() {
        val commandNames = listOf("plan", "describe", "edit", "custom-command")

        commandNames.forEach { commandName ->
            val state = CommandState(
                commandName = commandName,
                currentStage = CommandStage.PLANNING,
                currentStep = 1,
                expectedAction = "Test"
            )

            val output = captureOutput { renderer.renderFsmState(state) }

            assertTrue(output.contains("[DEBUG] Command: $commandName"))
        }
    }

    @Test
    fun `renderFsmState should handle large step numbers`() {
        val state = CommandState(
            commandName = "plan",
            currentStage = CommandStage.EXECUTION,
            currentStep = 999,
            expectedAction = "Processing"
        )

        val output = captureOutput { renderer.renderFsmState(state) }

        assertTrue(output.contains("[DEBUG] Step: 999"))
    }

    @Test
    fun `renderFsmState should handle context with special characters`() {
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

        assertTrue(output.contains("key-with-dash: value with spaces"))
        assertTrue(output.contains("key_with_underscore: value@with#special\$chars"))
    }
}
