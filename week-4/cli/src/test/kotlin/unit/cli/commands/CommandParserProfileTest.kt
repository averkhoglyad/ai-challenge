package io.averkhogliad.ai.challenge.week4.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CommandParserProfileTest : FreeSpec({

    fun createTaskContext(): CommandContext {
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

    "Profile List Parsing" - {
        "should parse profile-list command" {
            // given
            // when
            val result = CommandParser.parse(":profile-list", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileList>()
        }

        "should parse profile-list with extra spaces" {
            // given
            // when
            val result = CommandParser.parse(":profile-list   ", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileList>()
        }
    }

    "Profile New Parsing" - {
        "should parse profile-new with name" {
            // given
            // when
            val result = CommandParser.parse(":profile-new Senior Dev", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileNew>()
            result.name shouldBe "Senior Dev"
        }

        "should parse profile-new with multi-word name" {
            // given
            // when
            val result = CommandParser.parse(":profile-new My Awesome Profile", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileNew>()
            result.name shouldBe "My Awesome Profile"
        }

        "should return Unknown for profile-new without name" {
            // given
            // when
            val result = CommandParser.parse(":profile-new", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "should return Unknown for profile-new with only spaces" {
            // given
            // when
            val result = CommandParser.parse(":profile-new   ", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    "Profile Use Parsing" - {
        "should parse profile-use with name" {
            // given
            // when
            val result = CommandParser.parse(":profile-use MyProfile", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileUse>()
            result.name shouldBe "MyProfile"
        }

        "should parse profile-use with multi-word name" {
            // given
            // when
            val result = CommandParser.parse(":profile-use Senior Developer", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileUse>()
            result.name shouldBe "Senior Developer"
        }

        "should parse profile-use none for deactivation" {
            // given
            // when
            val result = CommandParser.parse(":profile-use none", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileUse>()
            result.name shouldBe "none"
        }

        "should return Unknown for profile-use without name" {
            // given
            // when
            val result = CommandParser.parse(":profile-use", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "should return Unknown for profile-use with only spaces" {
            // given
            // when
            val result = CommandParser.parse(":profile-use   ", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    "Error handling" - {
        "should return Unknown for unknown profile command" {
            // given
            // when
            val result = CommandParser.parse(":profile-unknown something", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "should reject profile commands when not in context" {
            // given
            // when
            val result = CommandParser.parse(":profile-list", CommandContext.TASK_SELECTION)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    "Profile Edit Parsing" - {
        "should parse profile-edit with name" {
            // given
            // when
            val result = CommandParser.parse(":profile-edit Senior Dev", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileEdit>()
            result.name shouldBe "Senior Dev"
        }

        "should return Unknown for profile-edit without name" {
            // given
            // when
            val result = CommandParser.parse(":profile-edit", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    "Profile Delete Parsing" - {
        "should parse profile-delete with name" {
            // given
            // when
            val result = CommandParser.parse(":profile-delete MyProfile", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileDelete>()
            result.name shouldBe "MyProfile"
        }

        "should return Unknown for profile-delete without name" {
            // given
            // when
            val result = CommandParser.parse(":profile-delete", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    "Profile Show Parsing" - {
        "should parse profile-show with name" {
            // given
            // when
            val result = CommandParser.parse(":profile-show MyProfile", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileShow>()
            result.name shouldBe "MyProfile"
        }

        "should parse profile-show without name (show active)" {
            // given
            // when
            val result = CommandParser.parse(":profile-show", createTaskContext())
            // then
            result.shouldBeInstanceOf<Command.ProfileShow>()
            result.name shouldBe null
        }
    }
})
