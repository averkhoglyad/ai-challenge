package io.averkhogliad.ai.challenge.week2.cli.commands

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandParserReachabilityTest {
    private val activeContext = CommandContext.activeTaskContext()

    @Test
    fun `active context parses goto commands routed by dispatcher`() {
        assertEquals(Command.Goto, CommandParser.parse(":goto", activeContext))

        val gotoState = CommandParser.parse(":goto validation", activeContext)
        assertIs<Command.GotoState>(gotoState)
        assertEquals("VALIDATION", gotoState.targetStage)

    }

    @Test
    fun `active context parses invariant commands routed by dispatcher`() {
        assertEquals(Command.InvariantList, CommandParser.parse(":invariant list", activeContext))

        val add = CommandParser.parse(":invariant add Always answer briefly", activeContext)
        assertIs<Command.InvariantAdd>(add)
        assertEquals("Always answer briefly", add.rule)

        val remove = CommandParser.parse(":invariant remove 2", activeContext)
        assertIs<Command.InvariantRemove>(remove)
        assertEquals(2, remove.id)
    }

    @Test
    fun `active context parses legacy dialog commands routed to unsupported message`() {
        assertIs<Command.NewDialog>(CommandParser.parse(":new old", activeContext))
        assertIs<Command.DeleteDialog>(CommandParser.parse(":delete old", activeContext))
        assertIs<Command.SwitchDialog>(CommandParser.parse(":switch old", activeContext))
        assertIs<Command.ShowHistory>(CommandParser.parse(":history", activeContext))
    }

    @Test
    fun `active context parses other dispatcher routed command roots`() {
        assertEquals(Command.ShowCompressionStatus, CommandParser.parse(":compression status", activeContext))

        assertEquals(Command.ShowCompressionStatus, CommandParser.parse(":comp status", activeContext))
        assertEquals(Command.ShowStrategyMenu, CommandParser.parse(":strategy", activeContext))
        assertIs<Command.CreateBranch>(CommandParser.parse(":branch create experiment", activeContext))
        assertEquals(Command.CreateCheckpoint, CommandParser.parse(":checkpoint", activeContext))
        assertEquals(Command.ListFacts, CommandParser.parse(":facts", activeContext))
    }
}
