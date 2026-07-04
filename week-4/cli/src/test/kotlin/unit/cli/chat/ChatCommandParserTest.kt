package io.averkhogliad.ai.challenge.week4.cli.unit.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatCommand
import io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatCommandParser
import io.averkhogliad.ai.challenge.week4.cli.cli.chat.TaskStateCommand
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Тесты для [ChatCommandParser] — парсера команд чата и памяти задачи.
 *
 * Проверяют:
 * - Корректный парсинг всех chat-команд
 * - Корректный парсинг всех task-state команд
 * - Возврат null для невалидных команд
 * - Возврат null для отсутствующих аргументов
 */
class ChatCommandParserTest : FreeSpec({

    // ═══════════════════════════════════════════════════════════════
    // parseChatCommand
    // ═══════════════════════════════════════════════════════════════

    "parseChatCommand" - {

        "chat-new → ChatCommand.New" {
            val result = ChatCommandParser.parseChatCommand("chat-new", "")
            result shouldBe ChatCommand.New
        }

        "chat-list → ChatCommand.List" {
            val result = ChatCommandParser.parseChatCommand("chat-list", "")
            result shouldBe ChatCommand.List
        }

        "chat-switch <id> → ChatCommand.Switch" {
            val result = ChatCommandParser.parseChatCommand("chat-switch", "abc123")
            result.shouldBeInstanceOf<ChatCommand.Switch>()
            (result as ChatCommand.Switch).id shouldBe "abc123"
        }

        "chat-switch без аргументов → null" {
            val result = ChatCommandParser.parseChatCommand("chat-switch", "")
            result shouldBe null
        }

        "chat-rename <name> → ChatCommand.Rename" {
            val result = ChatCommandParser.parseChatCommand("chat-rename", "My Chat")
            result.shouldBeInstanceOf<ChatCommand.Rename>()
            (result as ChatCommand.Rename).name shouldBe "My Chat"
        }

        "chat-rename без аргументов → null" {
            val result = ChatCommandParser.parseChatCommand("chat-rename", "")
            result shouldBe null
        }

        "chat-delete <id> → ChatCommand.Delete" {
            val result = ChatCommandParser.parseChatCommand("chat-delete", "abc123")
            result.shouldBeInstanceOf<ChatCommand.Delete>()
            (result as ChatCommand.Delete).id shouldBe "abc123"
        }

        "chat-delete без аргументов → null" {
            val result = ChatCommandParser.parseChatCommand("chat-delete", "")
            result shouldBe null
        }

        "chat-archive → ChatCommand.Archive" {
            val result = ChatCommandParser.parseChatCommand("chat-archive", "")
            result shouldBe ChatCommand.Archive
        }

        "chat-history без аргументов → ChatCommand.History(10)" {
            val result = ChatCommandParser.parseChatCommand("chat-history", "")
            result.shouldBeInstanceOf<ChatCommand.History>()
            (result as ChatCommand.History).limit shouldBe 10
        }

        "chat-history 5 → ChatCommand.History(5)" {
            val result = ChatCommandParser.parseChatCommand("chat-history", "5")
            result.shouldBeInstanceOf<ChatCommand.History>()
            (result as ChatCommand.History).limit shouldBe 5
        }

        "chat-history с нечисловым аргументом → ChatCommand.History(10) (fallback)" {
            val result = ChatCommandParser.parseChatCommand("chat-history", "abc")
            result.shouldBeInstanceOf<ChatCommand.History>()
            (result as ChatCommand.History).limit shouldBe 10
        }

        "невалидная команда → null" {
            ChatCommandParser.parseChatCommand("chat-unknown", "") shouldBe null
            ChatCommandParser.parseChatCommand("invalid", "arg") shouldBe null
            ChatCommandParser.parseChatCommand("", "") shouldBe null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // parseTaskStateCommand
    // ═══════════════════════════════════════════════════════════════

    "parseTaskStateCommand" - {

        "task-state → TaskStateCommand.Show" {
            val result = ChatCommandParser.parseTaskStateCommand("task-state", "")
            result shouldBe TaskStateCommand.Show
        }

        "task-reset → TaskStateCommand.Reset" {
            val result = ChatCommandParser.parseTaskStateCommand("task-reset", "")
            result shouldBe TaskStateCommand.Reset
        }

        "task-goal <text> → TaskStateCommand.SetGoal" {
            val result = ChatCommandParser.parseTaskStateCommand("task-goal", "Build a web app")
            result.shouldBeInstanceOf<TaskStateCommand.SetGoal>()
            (result as TaskStateCommand.SetGoal).text shouldBe "Build a web app"
        }

        "task-goal без аргументов → null" {
            val result = ChatCommandParser.parseTaskStateCommand("task-goal", "")
            result shouldBe null
        }

        "task-term add <name> <definition> → TaskStateCommand.AddTerm" {
            val result =
                ChatCommandParser.parseTaskStateCommand("task-term", "add API Application Programming Interface")
            result.shouldBeInstanceOf<TaskStateCommand.AddTerm>()
            val cmd = result as TaskStateCommand.AddTerm
            cmd.name shouldBe "API"
            cmd.definition shouldBe "Application Programming Interface"
        }

        "task-term add без definition → null" {
            val result = ChatCommandParser.parseTaskStateCommand("task-term", "add API")
            result shouldBe null
        }

        "task-term add без аргументов → null" {
            val result = ChatCommandParser.parseTaskStateCommand("task-term", "add")
            result shouldBe null
        }

        "task-term remove <name> → TaskStateCommand.RemoveTerm" {
            val result = ChatCommandParser.parseTaskStateCommand("task-term", "remove API")
            result.shouldBeInstanceOf<TaskStateCommand.RemoveTerm>()
            (result as TaskStateCommand.RemoveTerm).name shouldBe "API"
        }

        "task-term remove без аргументов → null" {
            val result = ChatCommandParser.parseTaskStateCommand("task-term", "remove")
            result shouldBe null
        }

        "task-term с невалидной подкомандой → null" {
            val result = ChatCommandParser.parseTaskStateCommand("task-term", "edit API")
            result shouldBe null
        }

        "task-constraint add <text> → TaskStateCommand.AddConstraint" {
            val result = ChatCommandParser.parseTaskStateCommand("task-constraint", "add Must be scalable")
            result.shouldBeInstanceOf<TaskStateCommand.AddConstraint>()
            (result as TaskStateCommand.AddConstraint).text shouldBe "Must be scalable"
        }

        "task-constraint add без текста → null" {
            val result = ChatCommandParser.parseTaskStateCommand("task-constraint", "add")
            result shouldBe null
        }

        "task-constraint remove <index> → TaskStateCommand.RemoveConstraint" {
            val result = ChatCommandParser.parseTaskStateCommand("task-constraint", "remove 0")
            result.shouldBeInstanceOf<TaskStateCommand.RemoveConstraint>()
            (result as TaskStateCommand.RemoveConstraint).index shouldBe 0
        }

        "task-constraint remove с нечисловым индексом → null" {
            val result = ChatCommandParser.parseTaskStateCommand("task-constraint", "remove abc")
            result shouldBe null
        }

        "task-constraint с невалидной подкомандой → null" {
            val result = ChatCommandParser.parseTaskStateCommand("task-constraint", "edit something")
            result shouldBe null
        }

        "невалидная команда → null" {
            ChatCommandParser.parseTaskStateCommand("task-unknown", "") shouldBe null
            ChatCommandParser.parseTaskStateCommand("", "") shouldBe null
        }
    }
})
