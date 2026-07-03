package io.averkhogliad.ai.challenge.week3.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Тесты для [CommandParser] — парсера команд CLI.
 *
 * Проверяют:
 * - Корректный парсинг основных команд (help, exit, clear, new, switch, history, etc.)
 * - Создание корректных Command моделей
 * - Обработку пользовательского ввода
 */
class CommandParserTest : FreeSpec({

    fun taskContext(taskId: Int): CommandContext {
        return CommandContext(
            currentTaskId = taskId,
            availableCommands = setOf(
                "help", "h", "quit", "q", "back", "b",
                "temp", "maxtokens", "reset", "params", "stop",
                "new", "list", "delete", "switch", "history",
                "compression", "comp", "strategy", "branch", "checkpoint", "facts",
                "clear", "status"
            )
        )
    }

    "Глобальные команды" - {
        "colon-help парсится как Help в любом контексте" {
            CommandParser.parse(":help") shouldBe Command.Help
            CommandParser.parse(":help", taskContext(1)) shouldBe Command.Help
            CommandParser.parse(":h") shouldBe Command.Help
        }

        "colon-quit парсится как Quit" {
            CommandParser.parse(":quit") shouldBe Command.Quit
            CommandParser.parse(":q") shouldBe Command.Quit
        }

        "colon-back парсится как Back" {
            CommandParser.parse(":back", taskContext(1)) shouldBe Command.Back
            CommandParser.parse(":b", taskContext(1)) shouldBe Command.Back
        }

        "глобальные команды не чувствительны к регистру" {
            CommandParser.parse(":HELP") shouldBe Command.Help
            CommandParser.parse(":QUIT") shouldBe Command.Quit
            CommandParser.parse(":BACK") shouldBe Command.Back
        }
    }

    "Пользовательский ввод" - {
        "пустой ввод даёт UserInput с пустым текстом" {
            val cmd = CommandParser.parse("")
            cmd.shouldBeInstanceOf<Command.UserInput>()
            cmd.text shouldBe ""
        }

        "обычный текст становится UserInput в контексте задачи" {
            val cmd = CommandParser.parse("Привет, как дела?", taskContext(1))
            cmd.shouldBeInstanceOf<Command.UserInput>()
            cmd.text shouldBe "Привет, как дела?"
        }

        "пробельный ввод даёт UserInput с пустым текстом" {
            val cmd = CommandParser.parse("   ")
            cmd.shouldBeInstanceOf<Command.UserInput>()
            cmd.text shouldBe ""
        }
    }

    "Выбор задачи (контекст без активной задачи)" - {
        "числовой ввод 1+ парсится как SelectTask" {
            CommandParser.parse("1") shouldBe Command.SelectTask(1)
            CommandParser.parse("5") shouldBe Command.SelectTask(5)
        }

        "0 парсится как Quit при выборе задачи" {
            CommandParser.parse("0") shouldBe Command.Quit
        }

        "нечисловой ввод при выборе задачи становится Unknown" {
            val cmd = CommandParser.parse("hello")
            cmd.shouldBeInstanceOf<Command.Unknown>()
            cmd.raw shouldBe "hello"
        }

        "отрицательное число становится Unknown" {
            val cmd = CommandParser.parse("-1")
            cmd.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    "LLM параметры" - {
        val ctx = taskContext(1)

        "colon-temp 0.7 парсится как SetTemperature" {
            val cmd = CommandParser.parse(":temp 0.7", ctx)
            cmd.shouldBeInstanceOf<Command.SetTemperature>()
            cmd.value shouldBe 0.7
        }

        "colon-temp без аргументов = ShowParameters" {
            CommandParser.parse(":temp", ctx) shouldBe Command.ShowParameters
        }

        "colon-temp с невалидным числом = Unknown" {
            CommandParser.parse(":temp abc", ctx).shouldBeInstanceOf<Command.Unknown>()
        }

        "colon-maxTokens 500 парсится как SetMaxTokens" {
            val cmd = CommandParser.parse(":maxTokens 500", ctx)
            cmd.shouldBeInstanceOf<Command.SetMaxTokens>()
            cmd.value shouldBe 500
        }

        "colon-maxTokens без аргументов = ShowParameters" {
            CommandParser.parse(":maxTokens", ctx) shouldBe Command.ShowParameters
        }

        "colon-maxTokens с невалидным числом = Unknown" {
            CommandParser.parse(":maxTokens xyz", ctx).shouldBeInstanceOf<Command.Unknown>()
        }

        "colon-stop END,DONE парсится как SetStopSequences" {
            val cmd = CommandParser.parse(":stop END,DONE", ctx)
            cmd.shouldBeInstanceOf<Command.SetStopSequences>()
            cmd.values shouldBe listOf("END", "DONE")
        }

        "colon-stop без аргументов = SetStopSequences с пустым списком" {
            val cmd = CommandParser.parse(":stop", ctx)
            cmd.shouldBeInstanceOf<Command.SetStopSequences>()
            cmd.values shouldBe emptyList()
        }

        "colon-stop с одной последовательностью" {
            val cmd = CommandParser.parse(":stop END", ctx)
            cmd.shouldBeInstanceOf<Command.SetStopSequences>()
            cmd.values shouldBe listOf("END")
        }

        "colon-reset парсится как ResetParameters" {
            CommandParser.parse(":reset", ctx) shouldBe Command.ResetParameters
        }

        "colon-params парсится как ShowParameters" {
            CommandParser.parse(":params", ctx) shouldBe Command.ShowParameters
        }
    }

    "Dialog команды" - {
        val ctx = taskContext(1)

        "colon-new парсится как NewDialog" {
            val cmd = CommandParser.parse(":new", ctx)
            cmd.shouldBeInstanceOf<Command.NewDialog>()
            cmd.title shouldBe "New Dialog"
        }

        "colon-new с заголовком парсится как NewDialog" {
            val cmd = CommandParser.parse(":new My Dialog", ctx)
            cmd.shouldBeInstanceOf<Command.NewDialog>()
            cmd.title shouldBe "My Dialog"
        }

        "colon-list парсится как ListTasks" {
            CommandParser.parse(":list", ctx).shouldBeInstanceOf<Command.ListTasks>()
        }

        "colon-delete с ID парсится как DeleteDialog" {
            val cmd = CommandParser.parse(":delete dialog-1", ctx)
            cmd.shouldBeInstanceOf<Command.DeleteDialog>()
            cmd.id shouldBe "dialog-1"
        }

        "colon-delete без аргументов = Unknown" {
            CommandParser.parse(":delete", ctx).shouldBeInstanceOf<Command.Unknown>()
        }

        "colon-switch с ID парсится как SwitchDialog" {
            val cmd = CommandParser.parse(":switch dialog-1", ctx)
            cmd.shouldBeInstanceOf<Command.SwitchDialog>()
            cmd.id shouldBe "dialog-1"
        }

        "colon-switch без аргументов = Unknown" {
            CommandParser.parse(":switch", ctx).shouldBeInstanceOf<Command.Unknown>()
        }

        "colon-history парсится как ShowHistory" {
            val cmd = CommandParser.parse(":history", ctx)
            cmd.shouldBeInstanceOf<Command.ShowHistory>()
            cmd.id shouldBe null
        }

        "colon-history с ID парсится как ShowHistory" {
            val cmd = CommandParser.parse(":history dialog-1", ctx)
            cmd.shouldBeInstanceOf<Command.ShowHistory>()
            cmd.id shouldBe "dialog-1"
        }
    }

    "Compression команды" - {
        val ctx = taskContext(1)

        "colon-compression on парсится как SetCompressionEnabled" {
            val cmd = CommandParser.parse(":compression on", ctx)
            cmd.shouldBeInstanceOf<Command.SetCompressionEnabled>()
            cmd.enabled shouldBe true
        }

        "colon-compression off парсится как SetCompressionEnabled" {
            val cmd = CommandParser.parse(":compression off", ctx)
            cmd.shouldBeInstanceOf<Command.SetCompressionEnabled>()
            cmd.enabled shouldBe false
        }

        "colon-compression window 10 парсится как SetCompressionWindow" {
            val cmd = CommandParser.parse(":compression window 10", ctx)
            cmd.shouldBeInstanceOf<Command.SetCompressionWindow>()
            cmd.size shouldBe 10
        }

        "colon-compression block 5 парсится как SetCompressionBlock" {
            val cmd = CommandParser.parse(":compression block 5", ctx)
            cmd.shouldBeInstanceOf<Command.SetCompressionBlock>()
            cmd.size shouldBe 5
        }

        "colon-compression status парсится как ShowCompressionStatus" {
            CommandParser.parse(":compression status", ctx) shouldBe Command.ShowCompressionStatus
        }

        "colon-comp on парсится как SetCompressionEnabled" {
            val cmd = CommandParser.parse(":comp on", ctx)
            cmd.shouldBeInstanceOf<Command.SetCompressionEnabled>()
            cmd.enabled shouldBe true
        }
    }

    "Strategy команды" - {
        val ctx = taskContext(1)

        "colon-strategy без аргументов = ShowStrategyMenu" {
            CommandParser.parse(":strategy", ctx) shouldBe Command.ShowStrategyMenu
        }

        "colon-strategy info парсится как ShowCurrentStrategy" {
            CommandParser.parse(":strategy info", ctx) shouldBe Command.ShowCurrentStrategy
        }

        "colon-strategy 1 парсится как SwitchStrategy" {
            val cmd = CommandParser.parse(":strategy 1", ctx)
            cmd.shouldBeInstanceOf<Command.SwitchStrategy>()
            cmd.index shouldBe 1
        }

        "colon-branch create name парсится как CreateBranch" {
            val cmd = CommandParser.parse(":branch create test-branch", ctx)
            cmd.shouldBeInstanceOf<Command.CreateBranch>()
            cmd.name shouldBe "test-branch"
        }

        "colon-branch switch name парсится как SwitchBranch" {
            val cmd = CommandParser.parse(":branch switch test-branch", ctx)
            cmd.shouldBeInstanceOf<Command.SwitchBranch>()
            cmd.name shouldBe "test-branch"
        }

        "colon-branch list парсится как ListBranches" {
            CommandParser.parse(":branch list", ctx) shouldBe Command.ListBranches
        }

        "colon-checkpoint парсится как CreateCheckpoint" {
            CommandParser.parse(":checkpoint", ctx) shouldBe Command.CreateCheckpoint
        }

        "colon-checkpoint list парсится как ListCheckpoints" {
            CommandParser.parse(":checkpoint list", ctx) shouldBe Command.ListCheckpoints
        }

        "colon-facts парсится как ListFacts" {
            CommandParser.parse(":facts", ctx) shouldBe Command.ListFacts
        }

        "colon-facts clear парсится как ClearFacts" {
            CommandParser.parse(":facts clear", ctx) shouldBe Command.ClearFacts
        }

        "colon-facts add key=value парсится как AddFact" {
            val cmd = CommandParser.parse(":facts add key=value", ctx)
            cmd.shouldBeInstanceOf<Command.AddFact>()
            cmd.key shouldBe "key"
            cmd.value shouldBe "value"
        }

        "colon-facts remove key парсится как RemoveFact" {
            val cmd = CommandParser.parse(":facts remove key", ctx)
            cmd.shouldBeInstanceOf<Command.RemoveFact>()
            cmd.key shouldBe "key"
        }
    }

    "Memory команды" - {
        val ctx = taskContext(1)

        "colon-clear парсится как ClearMemory" {
            CommandParser.parse(":clear", ctx) shouldBe Command.ClearMemory
        }

        "colon-status парсится как ShowStatus" {
            CommandParser.parse(":status", ctx) shouldBe Command.ShowStatus
        }

        "memory команды не чувствительны к регистру" {
            CommandParser.parse(":CLEAR", ctx) shouldBe Command.ClearMemory
            CommandParser.parse(":STATUS", ctx) shouldBe Command.ShowStatus
        }
    }

    "Unknown команды" - {
        "неизвестная команда парсится как Unknown" {
            val cmd = CommandParser.parse(":unknown")
            cmd.shouldBeInstanceOf<Command.Unknown>()
            cmd.raw shouldBe ":unknown"
        }

        "неизвестная команда с аргументами парсится как Unknown" {
            val cmd = CommandParser.parse(":unknown arg1 arg2")
            cmd.shouldBeInstanceOf<Command.Unknown>()
            cmd.raw shouldBe ":unknown arg1 arg2"
        }
    }
})
