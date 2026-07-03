package io.averkhogliad.ai.challenge.week4.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandParser
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

    ":state command parsing" - {
        "parse state command" {
            // given
            val context = CommandContext.TASK_SELECTION
            // when
            val command = CommandParser.parse(":state", context)
            // then
            command shouldBe Command.ShowState
        }

        "parse state command case insensitive" {
            // given
            val context = CommandContext.TASK_SELECTION
            // when
            val command = CommandParser.parse(":STATE", context)
            // then
            command shouldBe Command.ShowState
        }

        "parse state command with mixed case" {
            // given
            val context = CommandContext.TASK_SELECTION
            // when
            val command = CommandParser.parse(":State", context)
            // then
            command shouldBe Command.ShowState
        }
    }

    ":abort command parsing" - {
        "parse abort command" {
            // given
            val context = CommandContext.TASK_SELECTION
            // when
            val command = CommandParser.parse(":abort", context)
            // then
            command shouldBe Command.Abort
        }

        "parse abort command case insensitive" {
            // given
            val context = CommandContext.TASK_SELECTION
            // when
            val command = CommandParser.parse(":ABORT", context)
            // then
            command shouldBe Command.Abort
        }

        "parse abort command with mixed case" {
            // given
            val context = CommandContext.TASK_SELECTION
            // when
            val command = CommandParser.parse(":Abort", context)
            // then
            command shouldBe Command.Abort
        }
    }
})
