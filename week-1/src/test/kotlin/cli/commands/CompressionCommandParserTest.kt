package io.averkhogliad.ai.challenge.week1.cli.commands

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressionCommandParserTest {

    // Контекст с активной задачей, в котором доступна команда compression
    private val taskContext = CommandContext(
        currentTaskId = 4,
        availableCommands = setOf("compression", "comp", "help", "quit", "back")
    )

    @Test
    fun `should parse compression on command`() {
        val result = CommandParser.parse(":compression on", taskContext)
        assertTrue(result is Command.SetCompressionEnabled)
        assertEquals(true, (result as Command.SetCompressionEnabled).enabled)
    }

    @Test
    fun `should parse compression off command`() {
        val result = CommandParser.parse(":compression off", taskContext)
        assertTrue(result is Command.SetCompressionEnabled)
        assertEquals(false, (result as Command.SetCompressionEnabled).enabled)
    }

    @Test
    fun `should parse compression window command`() {
        val result = CommandParser.parse(":compression window 15", taskContext)
        assertTrue(result is Command.SetCompressionWindow)
        assertEquals(15, (result as Command.SetCompressionWindow).size)
    }

    @Test
    fun `should parse compression block command`() {
        val result = CommandParser.parse(":compression block 3", taskContext)
        assertTrue(result is Command.SetCompressionBlock)
        assertEquals(3, (result as Command.SetCompressionBlock).size)
    }

    @Test
    fun `should parse compression status command`() {
        val result = CommandParser.parse(":compression status", taskContext)
        assertTrue(result is Command.ShowCompressionStatus)
    }

    @Test
    fun `should return Unknown for invalid compression subcommand`() {
        val result = CommandParser.parse(":compression invalid", taskContext)
        assertTrue(result is Command.Unknown)
    }

    @Test
    fun `should return Unknown for compression window with invalid number`() {
        val result = CommandParser.parse(":compression window abc", taskContext)
        assertTrue(result is Command.Unknown)
    }

    @Test
    fun `should return Unknown for compression window with zero`() {
        val result = CommandParser.parse(":compression window 0", taskContext)
        assertTrue(result is Command.Unknown)
    }

    @Test
    fun `should return Unknown for compression window with negative number`() {
        val result = CommandParser.parse(":compression window -5", taskContext)
        assertTrue(result is Command.Unknown)
    }

    @Test
    fun `should return Unknown for compression block with invalid number`() {
        val result = CommandParser.parse(":compression block xyz", taskContext)
        assertTrue(result is Command.Unknown)
    }

    @Test
    fun `should return Unknown for compression block with zero`() {
        val result = CommandParser.parse(":compression block 0", taskContext)
        assertTrue(result is Command.Unknown)
    }

    @Test
    fun `should parse comp alias for compression on`() {
        val result = CommandParser.parse(":comp on", taskContext)
        assertTrue(result is Command.SetCompressionEnabled)
        assertEquals(true, (result as Command.SetCompressionEnabled).enabled)
    }

    @Test
    fun `should parse comp alias for compression window`() {
        val result = CommandParser.parse(":comp window 20", taskContext)
        assertTrue(result is Command.SetCompressionWindow)
        assertEquals(20, (result as Command.SetCompressionWindow).size)
    }
}
