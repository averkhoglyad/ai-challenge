package io.averkhogliad.ai.challenge.week3.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Unit-тесты для парсинга команд `:state` и `:abort`.
 *
 * Проверяет:
 * - Парсинг команды `:state` (показать состояние FSM)
 * - Парсинг команды `:abort` (прервать активную команду)
 * - Регистронезависимость команд
 */
class CommandParserStateAbortTest : FreeSpec({

    "parse state command" {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":state", context)
        command shouldBe Command.ShowState
    }

    "parse state command case insensitive" {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":STATE", context)
        command shouldBe Command.ShowState
    }

    "parse state command with mixed case" {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":State", context)
        command shouldBe Command.ShowState
    }

    "parse abort command" {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":abort", context)
        command shouldBe Command.Abort
    }

    "parse abort command case insensitive" {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":ABORT", context)
        command shouldBe Command.Abort
    }

    "parse abort command with mixed case" {
        val context = CommandContext.TASK_SELECTION
        val command = CommandParser.parse(":Abort", context)
        command shouldBe Command.Abort
    }
})
