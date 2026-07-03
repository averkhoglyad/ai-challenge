package io.averkhogliad.ai.challenge.week4.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CommandParserReachabilityTest : FreeSpec({

    "Active context" - {
        val activeContext = CommandContext.activeTaskContext()

        "parses goto commands routed by dispatcher" {
            // given
            // when
            val goto = CommandParser.parse(":goto", activeContext)
            val gotoState = CommandParser.parse(":goto validation", activeContext)
            // then
            goto shouldBe Command.Goto
            gotoState.shouldBeInstanceOf<Command.GotoState>()
            gotoState.targetStage shouldBe "VALIDATION"
        }

        "parses invariant commands routed by dispatcher" {
            // given
            // when
            val list = CommandParser.parse(":invariant list", activeContext)
            val add = CommandParser.parse(":invariant add Always answer briefly", activeContext)
            val remove = CommandParser.parse(":invariant remove 2", activeContext)
            // then
            list shouldBe Command.InvariantList
            add.shouldBeInstanceOf<Command.InvariantAdd>()
            add.rule shouldBe "Always answer briefly"
            remove.shouldBeInstanceOf<Command.InvariantRemove>()
            remove.id shouldBe 2
        }

        "parses legacy dialog commands routed to unsupported message" {
            // given
            // when
            val newDialog = CommandParser.parse(":new old", activeContext)
            val deleteDialog = CommandParser.parse(":delete old", activeContext)
            val switchDialog = CommandParser.parse(":switch old", activeContext)
            val history = CommandParser.parse(":history", activeContext)
            // then
            newDialog.shouldBeInstanceOf<Command.NewDialog>()
            deleteDialog.shouldBeInstanceOf<Command.DeleteDialog>()
            switchDialog.shouldBeInstanceOf<Command.SwitchDialog>()
            history.shouldBeInstanceOf<Command.ShowHistory>()
        }

        "parses other dispatcher routed command roots" {
            // given
            // when
            val compStatus = CommandParser.parse(":compression status", activeContext)
            val compShort = CommandParser.parse(":comp status", activeContext)
            val strategy = CommandParser.parse(":strategy", activeContext)
            val branch = CommandParser.parse(":branch create experiment", activeContext)
            val checkpoint = CommandParser.parse(":checkpoint", activeContext)
            val facts = CommandParser.parse(":facts", activeContext)
            // then
            compStatus shouldBe Command.ShowCompressionStatus
            compShort shouldBe Command.ShowCompressionStatus
            strategy shouldBe Command.ShowStrategyMenu
            branch.shouldBeInstanceOf<Command.CreateBranch>()
            checkpoint shouldBe Command.CreateCheckpoint
            facts shouldBe Command.ListFacts
        }
    }
})
