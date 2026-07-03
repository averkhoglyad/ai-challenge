package io.averkhogliad.ai.challenge.week3.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugAction
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CommandParserDebugTest : FreeSpec({

    "parseDebugCommand with empty args returns TOGGLE" {
        val result = CommandParser.parseDebugCommand("", ":debug")
        result.shouldBeInstanceOf<Command.Debug>()
        (result as Command.Debug).action shouldBe DebugAction.TOGGLE
    }

    "parseDebugCommand with 'on' returns ON" {
        val result = CommandParser.parseDebugCommand("on", ":debug on")
        result.shouldBeInstanceOf<Command.Debug>()
        (result as Command.Debug).action shouldBe DebugAction.ON
    }

    "parseDebugCommand with 'off' returns OFF" {
        val result = CommandParser.parseDebugCommand("off", ":debug off")
        result.shouldBeInstanceOf<Command.Debug>()
        (result as Command.Debug).action shouldBe DebugAction.OFF
    }

    "parseDebugCommand with unknown arg returns Unknown" {
        val result = CommandParser.parseDebugCommand("invalid", ":debug invalid")
        result.shouldBeInstanceOf<Command.Unknown>()
        (result as Command.Unknown).raw shouldBe ":debug invalid"
    }

    "parseDebugCommand is case-insensitive" {
        val resultOn = CommandParser.parseDebugCommand("ON", ":debug ON")
        resultOn.shouldBeInstanceOf<Command.Debug>()
        (resultOn as Command.Debug).action shouldBe DebugAction.ON

        val resultOff = CommandParser.parseDebugCommand("OFF", ":debug OFF")
        resultOff.shouldBeInstanceOf<Command.Debug>()
        (resultOff as Command.Debug).action shouldBe DebugAction.OFF
    }
})
