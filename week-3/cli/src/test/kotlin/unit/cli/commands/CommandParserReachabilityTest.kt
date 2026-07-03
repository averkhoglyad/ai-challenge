package io.averkhogliad.ai.challenge.week3.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CommandParserReachabilityTest : FreeSpec({
    val activeContext = CommandContext.activeTaskContext()

    "active context parses goto commands routed by dispatcher" {
        CommandParser.parse(":goto", activeContext) shouldBe Command.Goto

        val gotoState = CommandParser.parse(":goto validation", activeContext)
        gotoState.shouldBeInstanceOf<Command.GotoState>()
        gotoState.targetStage shouldBe "VALIDATION"
    }

    "active context parses invariant commands routed by dispatcher" {
        CommandParser.parse(":invariant list", activeContext) shouldBe Command.InvariantList

        val add = CommandParser.parse(":invariant add Always answer briefly", activeContext)
        add.shouldBeInstanceOf<Command.InvariantAdd>()
        add.rule shouldBe "Always answer briefly"

        val remove = CommandParser.parse(":invariant remove 2", activeContext)
        remove.shouldBeInstanceOf<Command.InvariantRemove>()
        remove.id shouldBe 2
    }

    "active context parses legacy dialog commands routed to unsupported message" {
        CommandParser.parse(":new old", activeContext).shouldBeInstanceOf<Command.NewDialog>()
        CommandParser.parse(":delete old", activeContext).shouldBeInstanceOf<Command.DeleteDialog>()
        CommandParser.parse(":switch old", activeContext).shouldBeInstanceOf<Command.SwitchDialog>()
        CommandParser.parse(":history", activeContext).shouldBeInstanceOf<Command.ShowHistory>()
    }

    "active context parses other dispatcher routed command roots" {
        CommandParser.parse(":compression status", activeContext) shouldBe Command.ShowCompressionStatus
        CommandParser.parse(":comp status", activeContext) shouldBe Command.ShowCompressionStatus
        CommandParser.parse(":strategy", activeContext) shouldBe Command.ShowStrategyMenu
        CommandParser.parse(":branch create experiment", activeContext).shouldBeInstanceOf<Command.CreateBranch>()
        CommandParser.parse(":checkpoint", activeContext) shouldBe Command.CreateCheckpoint
        CommandParser.parse(":facts", activeContext) shouldBe Command.ListFacts
    }
})
