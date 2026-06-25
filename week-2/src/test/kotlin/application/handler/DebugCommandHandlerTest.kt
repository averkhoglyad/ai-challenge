package io.averkhogliad.ai.challenge.week2.application.handler

import io.averkhogliad.ai.challenge.week2.domain.model.DebugMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DebugCommandHandlerTest {

    private lateinit var debugMode: DebugMode
    private lateinit var executor: DebugCommandHandler

    @BeforeEach
    fun setUp() {
        debugMode = DebugMode()
        executor = DebugCommandHandler(debugMode)
    }

    @Test
    fun `execute TOGGLE when disabled enables debug mode`() {
        assertFalse(debugMode.isEnabled)

        val result = executor.execute(DebugAction.TOGGLE)

        assertTrue(debugMode.isEnabled)
        assertEquals("Debug mode enabled", result)
    }

    @Test
    fun `execute TOGGLE when enabled disables debug mode`() {
        debugMode.enable()
        assertTrue(debugMode.isEnabled)

        val result = executor.execute(DebugAction.TOGGLE)

        assertFalse(debugMode.isEnabled)
        assertEquals("Debug mode disabled", result)
    }

    @Test
    fun `execute ON enables debug mode`() {
        assertFalse(debugMode.isEnabled)

        val result = executor.execute(DebugAction.ON)

        assertTrue(debugMode.isEnabled)
        assertEquals("Debug mode enabled", result)
    }

    @Test
    fun `execute ON when already enabled returns message`() {
        debugMode.enable()
        assertTrue(debugMode.isEnabled)

        val result = executor.execute(DebugAction.ON)

        assertTrue(debugMode.isEnabled)
        assertEquals("Debug mode already enabled", result)
    }

    @Test
    fun `execute OFF disables debug mode`() {
        debugMode.enable()
        assertTrue(debugMode.isEnabled)

        val result = executor.execute(DebugAction.OFF)

        assertFalse(debugMode.isEnabled)
        assertEquals("Debug mode disabled", result)
    }

    @Test
    fun `execute OFF when already disabled returns message`() {
        assertFalse(debugMode.isEnabled)

        val result = executor.execute(DebugAction.OFF)

        assertFalse(debugMode.isEnabled)
        assertEquals("Debug mode already disabled", result)
    }

    @Test
    fun `isEnabled returns current debug mode state`() {
        assertFalse(executor.isEnabled())

        debugMode.enable()
        assertTrue(executor.isEnabled())

        debugMode.disable()
        assertFalse(executor.isEnabled())
    }

    @Test
    fun `commandName is debug`() {
        assertEquals("debug", executor.commandName)
    }
}
