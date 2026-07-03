package io.averkhogliad.ai.challenge.week4.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week4.cli.application.handler.DebugAction
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CommandParserDebugTest : FreeSpec({

    "parseDebugCommand" - {
        "with empty args returns TOGGLE" {
            // given
            // when
            val result = CommandParser.parseDebugCommand("", ":debug")
            // then
            result.shouldBeInstanceOf<Command.Debug>()
            (result as Command.Debug).action shouldBe DebugAction.TOGGLE
        }

        "with 'on' returns ON" {
            // given
            // when
            val result = CommandParser.parseDebugCommand("on", ":debug on")
            // then
            result.shouldBeInstanceOf<Command.Debug>()
            (result as Command.Debug).action shouldBe DebugAction.ON
        }

        "with 'off' returns OFF" {
            // given
            // when
            val result = CommandParser.parseDebugCommand("off", ":debug off")
            // then
            result.shouldBeInstanceOf<Command.Debug>()
            (result as Command.Debug).action shouldBe DebugAction.OFF
        }

        "with unknown arg returns Unknown" {
            // given
            // when
            val result = CommandParser.parseDebugCommand("invalid", ":debug invalid")
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
            (result as Command.Unknown).raw shouldBe ":debug invalid"
        }

        "is case-insensitive" {
            // given
            // when
            val resultOn = CommandParser.parseDebugCommand("ON", ":debug ON")
            // then
            resultOn.shouldBeInstanceOf<Command.Debug>()
            (resultOn as Command.Debug).action shouldBe DebugAction.ON

            // when
            val resultOff = CommandParser.parseDebugCommand("OFF", ":debug OFF")
            // then
            resultOff.shouldBeInstanceOf<Command.Debug>()
            (resultOff as Command.Debug).action shouldBe DebugAction.OFF
        }
    }
})
