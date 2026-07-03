package io.averkhogliad.ai.challenge.week4.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandParser
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

    // ═══════════════════════════════════════════════════════════════
    // Глобальные команды
    // ═══════════════════════════════════════════════════════════════

    "Глобальные команды" - {
        "colon-help парсится как Help в любом контексте" {
            // given
            // when
            val noCtx = CommandParser.parse(":help")
            val withCtx = CommandParser.parse(":help", taskContext(1))
            val short = CommandParser.parse(":h")
            // then
            noCtx shouldBe Command.Help
            withCtx shouldBe Command.Help
            short shouldBe Command.Help
        }

        "colon-quit парсится как Quit" {
            // given
            // when
            val full = CommandParser.parse(":quit")
            val short = CommandParser.parse(":q")
            // then
            full shouldBe Command.Quit
            short shouldBe Command.Quit
        }

        "colon-back парсится как Back" {
            // given
            val ctx = taskContext(1)
            // when
            val full = CommandParser.parse(":back", ctx)
            val short = CommandParser.parse(":b", ctx)
            // then
            full shouldBe Command.Back
            short shouldBe Command.Back
        }

        "глобальные команды не чувствительны к регистру" {
            // given
            // when
            val help = CommandParser.parse(":HELP")
            val quit = CommandParser.parse(":QUIT")
            val back = CommandParser.parse(":BACK")
            // then
            help shouldBe Command.Help
            quit shouldBe Command.Quit
            back shouldBe Command.Back
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Пользовательский ввод
    // ═══════════════════════════════════════════════════════════════

    "Пользовательский ввод" - {
        "пустой ввод даёт UserInput с пустым текстом" {
            // given
            // when
            val cmd = CommandParser.parse("")
            // then
            cmd.shouldBeInstanceOf<Command.UserInput>()
            cmd.text shouldBe ""
        }

        "обычный текст становится UserInput в контексте задачи" {
            // given
            val ctx = taskContext(1)
            // when
            val cmd = CommandParser.parse("Привет, как дела?", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.UserInput>()
            cmd.text shouldBe "Привет, как дела?"
        }

        "пробельный ввод даёт UserInput с пустым текстом" {
            // given
            // when
            val cmd = CommandParser.parse("   ")
            // then
            cmd.shouldBeInstanceOf<Command.UserInput>()
            cmd.text shouldBe ""
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Выбор задачи
    // ═══════════════════════════════════════════════════════════════

    "Выбор задачи (контекст без активной задачи)" - {
        "числовой ввод 1+ парсится как SelectTask" {
            // given
            // when
            val one = CommandParser.parse("1")
            val five = CommandParser.parse("5")
            // then
            one shouldBe Command.SelectTask(1)
            five shouldBe Command.SelectTask(5)
        }

        "0 парсится как Quit при выборе задачи" {
            // given
            // when
            val result = CommandParser.parse("0")
            // then
            result shouldBe Command.Quit
        }

        "нечисловой ввод при выборе задачи становится Unknown" {
            // given
            // when
            val cmd = CommandParser.parse("hello")
            // then
            cmd.shouldBeInstanceOf<Command.Unknown>()
            cmd.raw shouldBe "hello"
        }

        "отрицательное число становится Unknown" {
            // given
            // when
            val cmd = CommandParser.parse("-1")
            // then
            cmd.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LLM параметры
    // ═══════════════════════════════════════════════════════════════

    "LLM параметры" - {
        val ctx = taskContext(1)

        "colon-temp 0.7 парсится как SetTemperature" {
            // given
            // when
            val cmd = CommandParser.parse(":temp 0.7", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetTemperature>()
            cmd.value shouldBe 0.7
        }

        "colon-temp без аргументов = ShowParameters" {
            // given
            // when
            val result = CommandParser.parse(":temp", ctx)
            // then
            result shouldBe Command.ShowParameters
        }

        "colon-temp с невалидным числом = Unknown" {
            // given
            // when
            val result = CommandParser.parse(":temp abc", ctx)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "colon-maxTokens 500 парсится как SetMaxTokens" {
            // given
            // when
            val cmd = CommandParser.parse(":maxTokens 500", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetMaxTokens>()
            cmd.value shouldBe 500
        }

        "colon-maxTokens без аргументов = ShowParameters" {
            // given
            // when
            val result = CommandParser.parse(":maxTokens", ctx)
            // then
            result shouldBe Command.ShowParameters
        }

        "colon-maxTokens с невалидным числом = Unknown" {
            // given
            // when
            val result = CommandParser.parse(":maxTokens xyz", ctx)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "colon-stop END,DONE парсится как SetStopSequences" {
            // given
            // when
            val cmd = CommandParser.parse(":stop END,DONE", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetStopSequences>()
            cmd.values shouldBe listOf("END", "DONE")
        }

        "colon-stop без аргументов = SetStopSequences с пустым списком" {
            // given
            // when
            val cmd = CommandParser.parse(":stop", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetStopSequences>()
            cmd.values shouldBe emptyList()
        }

        "colon-stop с одной последовательностью" {
            // given
            // when
            val cmd = CommandParser.parse(":stop END", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetStopSequences>()
            cmd.values shouldBe listOf("END")
        }

        "colon-reset парсится как ResetParameters" {
            // given
            // when
            val result = CommandParser.parse(":reset", ctx)
            // then
            result shouldBe Command.ResetParameters
        }

        "colon-params парсится как ShowParameters" {
            // given
            // when
            val result = CommandParser.parse(":params", ctx)
            // then
            result shouldBe Command.ShowParameters
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Dialog команды
    // ═══════════════════════════════════════════════════════════════

    "Dialog команды" - {
        val ctx = taskContext(1)

        "colon-new парсится как NewDialog" {
            // given
            // when
            val cmd = CommandParser.parse(":new", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.NewDialog>()
            cmd.title shouldBe "New Dialog"
        }

        "colon-new с заголовком парсится как NewDialog" {
            // given
            // when
            val cmd = CommandParser.parse(":new My Dialog", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.NewDialog>()
            cmd.title shouldBe "My Dialog"
        }

        "colon-list парсится как ListTasks" {
            // given
            // when
            val result = CommandParser.parse(":list", ctx)
            // then
            result.shouldBeInstanceOf<Command.ListTasks>()
        }

        "colon-delete с ID парсится как DeleteDialog" {
            // given
            // when
            val cmd = CommandParser.parse(":delete dialog-1", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.DeleteDialog>()
            cmd.id shouldBe "dialog-1"
        }

        "colon-delete без аргументов = Unknown" {
            // given
            // when
            val result = CommandParser.parse(":delete", ctx)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "colon-switch с ID парсится как SwitchDialog" {
            // given
            // when
            val cmd = CommandParser.parse(":switch dialog-1", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SwitchDialog>()
            cmd.id shouldBe "dialog-1"
        }

        "colon-switch без аргументов = Unknown" {
            // given
            // when
            val result = CommandParser.parse(":switch", ctx)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "colon-history парсится как ShowHistory" {
            // given
            // when
            val cmd = CommandParser.parse(":history", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.ShowHistory>()
            cmd.id shouldBe null
        }

        "colon-history с ID парсится как ShowHistory" {
            // given
            // when
            val cmd = CommandParser.parse(":history dialog-1", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.ShowHistory>()
            cmd.id shouldBe "dialog-1"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Compression команды
    // ═══════════════════════════════════════════════════════════════

    "Compression команды" - {
        val ctx = taskContext(1)

        "colon-compression on парсится как SetCompressionEnabled" {
            // given
            // when
            val cmd = CommandParser.parse(":compression on", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetCompressionEnabled>()
            cmd.enabled shouldBe true
        }

        "colon-compression off парсится как SetCompressionEnabled" {
            // given
            // when
            val cmd = CommandParser.parse(":compression off", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetCompressionEnabled>()
            cmd.enabled shouldBe false
        }

        "colon-compression window 10 парсится как SetCompressionWindow" {
            // given
            // when
            val cmd = CommandParser.parse(":compression window 10", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetCompressionWindow>()
            cmd.size shouldBe 10
        }

        "colon-compression block 5 парсится как SetCompressionBlock" {
            // given
            // when
            val cmd = CommandParser.parse(":compression block 5", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetCompressionBlock>()
            cmd.size shouldBe 5
        }

        "colon-compression status парсится как ShowCompressionStatus" {
            // given
            // when
            val result = CommandParser.parse(":compression status", ctx)
            // then
            result shouldBe Command.ShowCompressionStatus
        }

        "colon-comp on парсится как SetCompressionEnabled" {
            // given
            // when
            val cmd = CommandParser.parse(":comp on", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SetCompressionEnabled>()
            cmd.enabled shouldBe true
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy команды
    // ═══════════════════════════════════════════════════════════════

    "Strategy команды" - {
        val ctx = taskContext(1)

        "colon-strategy без аргументов = ShowStrategyMenu" {
            // given
            // when
            val result = CommandParser.parse(":strategy", ctx)
            // then
            result shouldBe Command.ShowStrategyMenu
        }

        "colon-strategy info парсится как ShowCurrentStrategy" {
            // given
            // when
            val result = CommandParser.parse(":strategy info", ctx)
            // then
            result shouldBe Command.ShowCurrentStrategy
        }

        "colon-strategy 1 парсится как SwitchStrategy" {
            // given
            // when
            val cmd = CommandParser.parse(":strategy 1", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SwitchStrategy>()
            cmd.index shouldBe 1
        }

        "colon-branch create name парсится как CreateBranch" {
            // given
            // when
            val cmd = CommandParser.parse(":branch create test-branch", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.CreateBranch>()
            cmd.name shouldBe "test-branch"
        }

        "colon-branch switch name парсится как SwitchBranch" {
            // given
            // when
            val cmd = CommandParser.parse(":branch switch test-branch", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.SwitchBranch>()
            cmd.name shouldBe "test-branch"
        }

        "colon-branch list парсится как ListBranches" {
            // given
            // when
            val result = CommandParser.parse(":branch list", ctx)
            // then
            result shouldBe Command.ListBranches
        }

        "colon-checkpoint парсится как CreateCheckpoint" {
            // given
            // when
            val result = CommandParser.parse(":checkpoint", ctx)
            // then
            result shouldBe Command.CreateCheckpoint
        }

        "colon-checkpoint list парсится как ListCheckpoints" {
            // given
            // when
            val result = CommandParser.parse(":checkpoint list", ctx)
            // then
            result shouldBe Command.ListCheckpoints
        }

        "colon-facts парсится как ListFacts" {
            // given
            // when
            val result = CommandParser.parse(":facts", ctx)
            // then
            result shouldBe Command.ListFacts
        }

        "colon-facts clear парсится как ClearFacts" {
            // given
            // when
            val result = CommandParser.parse(":facts clear", ctx)
            // then
            result shouldBe Command.ClearFacts
        }

        "colon-facts add key=value парсится как AddFact" {
            // given
            // when
            val cmd = CommandParser.parse(":facts add key=value", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.AddFact>()
            cmd.key shouldBe "key"
            cmd.value shouldBe "value"
        }

        "colon-facts remove key парсится как RemoveFact" {
            // given
            // when
            val cmd = CommandParser.parse(":facts remove key", ctx)
            // then
            cmd.shouldBeInstanceOf<Command.RemoveFact>()
            cmd.key shouldBe "key"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Memory команды (Phase 3)
    // ═══════════════════════════════════════════════════════════════

    "Memory команды" - {
        val ctx = taskContext(1)

        "colon-clear парсится как ClearMemory" {
            // given
            // when
            val result = CommandParser.parse(":clear", ctx)
            // then
            result shouldBe Command.ClearMemory
        }

        "colon-status парсится как ShowStatus" {
            // given
            // when
            val result = CommandParser.parse(":status", ctx)
            // then
            result shouldBe Command.ShowStatus
        }

        "memory команды не чувствительны к регистру" {
            // given
            // when
            val clear = CommandParser.parse(":CLEAR", ctx)
            val status = CommandParser.parse(":STATUS", ctx)
            // then
            clear shouldBe Command.ClearMemory
            status shouldBe Command.ShowStatus
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Unknown команды
    // ═══════════════════════════════════════════════════════════════

    "Unknown команды" - {
        "неизвестная команда парсится как Unknown" {
            // given
            // when
            val cmd = CommandParser.parse(":unknown")
            // then
            cmd.shouldBeInstanceOf<Command.Unknown>()
            cmd.raw shouldBe ":unknown"
        }

        "неизвестная команда с аргументами парсится как Unknown" {
            // given
            // when
            val cmd = CommandParser.parse(":unknown arg1 arg2")
            // then
            cmd.shouldBeInstanceOf<Command.Unknown>()
            cmd.raw shouldBe ":unknown arg1 arg2"
        }
    }
})
