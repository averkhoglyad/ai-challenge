package io.averkhogliad.ai.challenge.week3.cli.cli.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit-тесты для парсинга команд `:state` и `:abort`.
 *
 * Проверяет:
 * - Парсинг команды `:state` (показать состояние FSM)
 * - Парсинг команды `:abort` (прервать активную команду)
 * - Регистронезависимость команд
 */
class CommandParserStateAbortTest {

    // ──── :state command parsing ────

    @Test
    fun `parse state command`() {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":state", context)
        assertEquals(Command.ShowState, command)
    }

    @Test
    fun `parse state command case insensitive`() {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":STATE", context)
        assertEquals(Command.ShowState, command)
    }

    @Test
    fun `parse state command with mixed case`() {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":State", context)
        assertEquals(Command.ShowState, command)
    }

    // ──── :abort command parsing ────

    @Test
    fun `parse abort command`() {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":abort", context)
        assertEquals(Command.Abort, command)
    }

    @Test
    fun `parse abort command case insensitive`() {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":ABORT", context)
        assertEquals(Command.Abort, command)
    }

    @Test
    fun `parse abort command with mixed case`() {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":Abort", context)
        assertEquals(Command.Abort, command)
    }
}
