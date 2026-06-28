package io.averkhogliad.ai.challenge.week3.cli.cli.commands

import io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandParserDebugTest {

    @Test
    fun `parseDebugCommand with empty args returns TOGGLE`() {
        val result = CommandParser.parseDebugCommand("", ":debug")
        assertTrue(result is Command.Debug)
        assertEquals(DebugAction.TOGGLE, (result as Command.Debug).action)
    }

    @Test
    fun `parseDebugCommand with 'on' returns ON`() {
        val result = CommandParser.parseDebugCommand("on", ":debug on")
        assertTrue(result is Command.Debug)
        assertEquals(DebugAction.ON, (result as Command.Debug).action)
    }

    @Test
    fun `parseDebugCommand with 'off' returns OFF`() {
        val result = CommandParser.parseDebugCommand("off", ":debug off")
        assertTrue(result is Command.Debug)
        assertEquals(DebugAction.OFF, (result as Command.Debug).action)
    }

    @Test
    fun `parseDebugCommand with unknown arg returns Unknown`() {
        val result = CommandParser.parseDebugCommand("invalid", ":debug invalid")
        assertTrue(result is Command.Unknown)
        assertEquals(":debug invalid", (result as Command.Unknown).raw)
    }

    @Test
    fun `parseDebugCommand is case-insensitive`() {
        val resultOn = CommandParser.parseDebugCommand("ON", ":debug ON")
        assertTrue(resultOn is Command.Debug)
        assertEquals(DebugAction.ON, (resultOn as Command.Debug).action)

        val resultOff = CommandParser.parseDebugCommand("OFF", ":debug OFF")
        assertTrue(resultOff is Command.Debug)
        assertEquals(DebugAction.OFF, (resultOff as Command.Debug).action)
    }
}
