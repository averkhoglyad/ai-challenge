package io.averkhogliad.ai.challenge.week3.cli.cli.commands

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CommandParserProfileTest {

    private fun createTaskContext(): CommandContext {
        return CommandContext(
            currentTaskId = 1,
            availableCommands = setOf(
                "help", "h", "quit", "q", "back", "b",
                "add", "list", "edit", "drop", "open", "close", "cancel",
                "step-add", "step-list", "step-done",
                "temp", "maxtokens", "reset", "params", "stop",
                "plan",
                "status", "clear", "ctx-save", "ctx-list", "ctx-forget",
                "profile-new", "profile-list", "profile-use",
                "profile-edit", "profile-delete", "profile-show"
            )
        )
    }

    @Nested
    inner class ProfileListParsing {
        @Test
        fun `should parse profile-list command`() {
            val result = CommandParser.parse(":profile-list", createTaskContext())
            assertIs<Command.ProfileList>(result)
        }

        @Test
        fun `should parse profile-list with extra spaces`() {
            val result = CommandParser.parse(":profile-list   ", createTaskContext())
            assertIs<Command.ProfileList>(result)
        }
    }

    @Nested
    inner class ProfileNewParsing {
        @Test
        fun `should parse profile-new with name`() {
            val result = CommandParser.parse(":profile-new Senior Dev", createTaskContext())
            assertIs<Command.ProfileNew>(result)
            assertEquals("Senior Dev", result.name)
        }

        @Test
        fun `should parse profile-new with multi-word name`() {
            val result = CommandParser.parse(":profile-new My Awesome Profile", createTaskContext())
            assertIs<Command.ProfileNew>(result)
            assertEquals("My Awesome Profile", result.name)
        }

        @Test
        fun `should return Unknown for profile-new without name`() {
            val result = CommandParser.parse(":profile-new", createTaskContext())
            assertIs<Command.Unknown>(result)
        }

        @Test
        fun `should return Unknown for profile-new with only spaces`() {
            val result = CommandParser.parse(":profile-new   ", createTaskContext())
            assertIs<Command.Unknown>(result)
        }
    }

    @Nested
    inner class ProfileUseParsing {
        @Test
        fun `should parse profile-use with name`() {
            val result = CommandParser.parse(":profile-use MyProfile", createTaskContext())
            assertIs<Command.ProfileUse>(result)
            assertEquals("MyProfile", result.name)
        }

        @Test
        fun `should parse profile-use with multi-word name`() {
            val result = CommandParser.parse(":profile-use Senior Developer", createTaskContext())
            assertIs<Command.ProfileUse>(result)
            assertEquals("Senior Developer", result.name)
        }

        @Test
        fun `should parse profile-use none for deactivation`() {
            val result = CommandParser.parse(":profile-use none", createTaskContext())
            assertIs<Command.ProfileUse>(result)
            assertEquals("none", result.name)
        }

        @Test
        fun `should return Unknown for profile-use without name`() {
            val result = CommandParser.parse(":profile-use", createTaskContext())
            assertIs<Command.Unknown>(result)
        }

        @Test
        fun `should return Unknown for profile-use with only spaces`() {
            val result = CommandParser.parse(":profile-use   ", createTaskContext())
            assertIs<Command.Unknown>(result)
        }
    }

    @Test
    fun `should return Unknown for unknown profile command`() {
        // Просто убеждаемся, что неизвестные profile-команды обрабатываются корректно
        val result = CommandParser.parse(":profile-unknown something", createTaskContext())
        assertIs<Command.Unknown>(result)
    }

    @Test
    fun `should reject profile commands when not in context`() {
        // Без контекста задачи команды profile-* не должны быть доступны
        val result = CommandParser.parse(":profile-list", CommandContext.TASK_SELECTION)
        assertIs<Command.Unknown>(result)
    }

    @Nested
    inner class ProfileEditParsing {
        @Test
        fun `should parse profile-edit with name`() {
            val result = CommandParser.parse(":profile-edit Senior Dev", createTaskContext())
            assertIs<Command.ProfileEdit>(result)
            assertEquals("Senior Dev", result.name)
        }

        @Test
        fun `should return Unknown for profile-edit without name`() {
            val result = CommandParser.parse(":profile-edit", createTaskContext())
            assertIs<Command.Unknown>(result)
        }
    }

    @Nested
    inner class ProfileDeleteParsing {
        @Test
        fun `should parse profile-delete with name`() {
            val result = CommandParser.parse(":profile-delete MyProfile", createTaskContext())
            assertIs<Command.ProfileDelete>(result)
            assertEquals("MyProfile", result.name)
        }

        @Test
        fun `should return Unknown for profile-delete without name`() {
            val result = CommandParser.parse(":profile-delete", createTaskContext())
            assertIs<Command.Unknown>(result)
        }
    }

    @Nested
    inner class ProfileShowParsing {
        @Test
        fun `should parse profile-show with name`() {
            val result = CommandParser.parse(":profile-show MyProfile", createTaskContext())
            assertIs<Command.ProfileShow>(result)
            assertEquals("MyProfile", result.name)
        }

        @Test
        fun `should parse profile-show without name (show active)`() {
            val result = CommandParser.parse(":profile-show", createTaskContext())
            assertIs<Command.ProfileShow>(result)
            assertNull(result.name)
        }
    }
}
