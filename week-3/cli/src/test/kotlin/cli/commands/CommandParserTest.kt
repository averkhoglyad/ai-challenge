package io.averkhogliad.ai.challenge.week3.cli.cli.commands

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Тесты для [CommandParser] — парсера команд CLI.
 *
 * Проверяют:
 * - Корректный парсинг основных команд (help, exit, clear, new, switch, history, etc.)
 * - Создание корректных Command моделей
 * - Обработку пользовательского ввода
 */
@DisplayName("CommandParser")
class CommandParserTest {

    // ═══════════════════════════════════════════════════════════════
    // Глобальные команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Глобальные команды")
    inner class GlobalCommands {

        @Test
        @DisplayName("colon-help парсится как Help в любом контексте")
        fun `help parsed as Help in any context`() {
            assertEquals(Command.Help, CommandParser.parse(":help"))
            assertEquals(Command.Help, CommandParser.parse(":help", taskContext(1)))
            assertEquals(Command.Help, CommandParser.parse(":h"))
        }

        @Test
        @DisplayName("colon-quit парсится как Quit")
        fun `quit parsed as Quit`() {
            assertEquals(Command.Quit, CommandParser.parse(":quit"))
            assertEquals(Command.Quit, CommandParser.parse(":q"))
        }

        @Test
        @DisplayName("colon-back парсится как Back")
        fun `back parsed as Back`() {
            assertEquals(Command.Back, CommandParser.parse(":back", taskContext(1)))
            assertEquals(Command.Back, CommandParser.parse(":b", taskContext(1)))
        }

        @Test
        @DisplayName("глобальные команды не чувствительны к регистру")
        fun `global commands are case insensitive`() {
            assertEquals(Command.Help, CommandParser.parse(":HELP"))
            assertEquals(Command.Quit, CommandParser.parse(":QUIT"))
            assertEquals(Command.Back, CommandParser.parse(":BACK"))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Пользовательский ввод
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Пользовательский ввод")
    inner class UserInput {

        @Test
        @DisplayName("пустой ввод даёт UserInput с пустым текстом")
        fun `empty input gives UserInput with blank text`() {
            val cmd = CommandParser.parse("")
            assertIs<Command.UserInput>(cmd)
            assertEquals("", cmd.text)
        }

        @Test
        @DisplayName("обычный текст становится UserInput в контексте задачи")
        fun `plain text becomes UserInput in task context`() {
            val cmd = CommandParser.parse("Привет, как дела?", taskContext(1))
            assertIs<Command.UserInput>(cmd)
            assertEquals("Привет, как дела?", cmd.text)
        }

        @Test
        @DisplayName("пробельный ввод даёт UserInput с пустым текстом")
        fun `whitespace input gives UserInput with blank text`() {
            val cmd = CommandParser.parse("   ")
            assertIs<Command.UserInput>(cmd)
            assertEquals("", cmd.text)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Выбор задачи
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Выбор задачи (контекст без активной задачи)")
    inner class TaskSelection {

        @Test
        @DisplayName("числовой ввод 1+ парсится как SelectTask")
        fun `numeric input 1+ parsed as SelectTask`() {
            assertEquals(Command.SelectTask(1), CommandParser.parse("1"))
            assertEquals(Command.SelectTask(5), CommandParser.parse("5"))
        }

        @Test
        @DisplayName("0 парсится как Quit при выборе задачи")
        fun `zero parsed as Quit in task selection`() {
            assertEquals(Command.Quit, CommandParser.parse("0"))
        }

        @Test
        @DisplayName("нечисловой ввод при выборе задачи становится Unknown")
        fun `non-numeric input in selection becomes UserInput`() {
            val cmd = CommandParser.parse("hello")
            assertIs<Command.Unknown>(cmd)
            assertEquals("hello", cmd.raw)
        }

        @Test
        @DisplayName("отрицательное число становится Unknown")
        fun `negative number becomes UserInput`() {
            val cmd = CommandParser.parse("-1")
            assertIs<Command.Unknown>(cmd)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LLM параметры
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("LLM параметры")
    inner class LlmParameters {

        private val ctx = taskContext(1)

        @Test
        @DisplayName("colon-temp 0.7 парсится как SetTemperature")
        fun `temp 0-7 parsed as SetTemperature`() {
            val cmd = CommandParser.parse(":temp 0.7", ctx)
            assertIs<Command.SetTemperature>(cmd)
            assertEquals(0.7, cmd.value)
        }

        @Test
        @DisplayName("colon-temp без аргументов = ShowParameters")
        fun `temp without args equals ShowParameters`() {
            assertEquals(Command.ShowParameters, CommandParser.parse(":temp", ctx))
        }

        @Test
        @DisplayName("colon-temp с невалидным числом = Unknown")
        fun `temp with invalid number equals Unknown`() {
            assertIs<Command.Unknown>(CommandParser.parse(":temp abc", ctx))
        }

        @Test
        @DisplayName("colon-maxTokens 500 парсится как SetMaxTokens")
        fun `maxTokens 500 parsed as SetMaxTokens`() {
            val cmd = CommandParser.parse(":maxTokens 500", ctx)
            assertIs<Command.SetMaxTokens>(cmd)
            assertEquals(500, cmd.value)
        }

        @Test
        @DisplayName("colon-maxTokens без аргументов = ShowParameters")
        fun `maxTokens without args equals ShowParameters`() {
            assertEquals(Command.ShowParameters, CommandParser.parse(":maxTokens", ctx))
        }

        @Test
        @DisplayName("colon-maxTokens с невалидным числом = Unknown")
        fun `maxTokens with invalid value equals Unknown`() {
            assertIs<Command.Unknown>(CommandParser.parse(":maxTokens xyz", ctx))
        }

        @Test
        @DisplayName("colon-stop END,DONE парсится как SetStopSequences")
        fun `stop END,DONE parsed as SetStopSequences`() {
            val cmd = CommandParser.parse(":stop END,DONE", ctx)
            assertIs<Command.SetStopSequences>(cmd)
            assertEquals(listOf("END", "DONE"), cmd.values)
        }

        @Test
        @DisplayName("colon-stop без аргументов = SetStopSequences с пустым списком")
        fun `stop without args equals empty list reset`() {
            val cmd = CommandParser.parse(":stop", ctx)
            assertIs<Command.SetStopSequences>(cmd)
            assertEquals(emptyList(), cmd.values)
        }

        @Test
        @DisplayName("colon-stop с одной последовательностью")
        fun `stop with single sequence`() {
            val cmd = CommandParser.parse(":stop END", ctx)
            assertIs<Command.SetStopSequences>(cmd)
            assertEquals(listOf("END"), cmd.values)
        }

        @Test
        @DisplayName("colon-reset парсится как ResetParameters")
        fun `reset parsed as ResetParameters`() {
            assertEquals(Command.ResetParameters, CommandParser.parse(":reset", ctx))
        }

        @Test
        @DisplayName("colon-params парсится как ShowParameters")
        fun `params parsed as ShowParameters`() {
            assertEquals(Command.ShowParameters, CommandParser.parse(":params", ctx))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Dialog команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dialog команды")
    inner class DialogCommands {

        private val ctx = taskContext(1)

        @Test
        @DisplayName("colon-new парсится как NewDialog")
        fun `new parsed as NewDialog`() {
            val cmd = CommandParser.parse(":new", ctx)
            assertIs<Command.NewDialog>(cmd)
            assertEquals("New Dialog", cmd.title)
        }

        @Test
        @DisplayName("colon-new с заголовком парсится как NewDialog")
        fun `new with title parsed as NewDialog`() {
            val cmd = CommandParser.parse(":new My Dialog", ctx)
            assertIs<Command.NewDialog>(cmd)
            assertEquals("My Dialog", cmd.title)
        }

        @Test
        @DisplayName("colon-list парсится как ListTasks")
        fun `list parsed as ListTasks`() {
            assertIs<Command.ListTasks>(CommandParser.parse(":list", ctx))
        }

        @Test
        @DisplayName("colon-delete с ID парсится как DeleteDialog")
        fun `delete with ID parsed as DeleteDialog`() {
            val cmd = CommandParser.parse(":delete dialog-1", ctx)
            assertIs<Command.DeleteDialog>(cmd)
            assertEquals("dialog-1", cmd.id)
        }

        @Test
        @DisplayName("colon-delete без аргументов = Unknown")
        fun `delete without args equals Unknown`() {
            assertIs<Command.Unknown>(CommandParser.parse(":delete", ctx))
        }

        @Test
        @DisplayName("colon-switch с ID парсится как SwitchDialog")
        fun `switch with ID parsed as SwitchDialog`() {
            val cmd = CommandParser.parse(":switch dialog-1", ctx)
            assertIs<Command.SwitchDialog>(cmd)
            assertEquals("dialog-1", cmd.id)
        }

        @Test
        @DisplayName("colon-switch без аргументов = Unknown")
        fun `switch without args equals Unknown`() {
            assertIs<Command.Unknown>(CommandParser.parse(":switch", ctx))
        }

        @Test
        @DisplayName("colon-history парсится как ShowHistory")
        fun `history parsed as ShowHistory`() {
            val cmd = CommandParser.parse(":history", ctx)
            assertIs<Command.ShowHistory>(cmd)
            assertEquals(null, cmd.id)
        }

        @Test
        @DisplayName("colon-history с ID парсится как ShowHistory")
        fun `history with ID parsed as ShowHistory`() {
            val cmd = CommandParser.parse(":history dialog-1", ctx)
            assertIs<Command.ShowHistory>(cmd)
            assertEquals("dialog-1", cmd.id)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Compression команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Compression команды")
    inner class CompressionCommands {

        private val ctx = taskContext(1)

        @Test
        @DisplayName("colon-compression on парсится как SetCompressionEnabled")
        fun `compression on parsed as SetCompressionEnabled`() {
            val cmd = CommandParser.parse(":compression on", ctx)
            assertIs<Command.SetCompressionEnabled>(cmd)
            assertEquals(true, cmd.enabled)
        }

        @Test
        @DisplayName("colon-compression off парсится как SetCompressionEnabled")
        fun `compression off parsed as SetCompressionEnabled`() {
            val cmd = CommandParser.parse(":compression off", ctx)
            assertIs<Command.SetCompressionEnabled>(cmd)
            assertEquals(false, cmd.enabled)
        }

        @Test
        @DisplayName("colon-compression window 10 парсится как SetCompressionWindow")
        fun `compression window 10 parsed as SetCompressionWindow`() {
            val cmd = CommandParser.parse(":compression window 10", ctx)
            assertIs<Command.SetCompressionWindow>(cmd)
            assertEquals(10, cmd.size)
        }

        @Test
        @DisplayName("colon-compression block 5 парсится как SetCompressionBlock")
        fun `compression block 5 parsed as SetCompressionBlock`() {
            val cmd = CommandParser.parse(":compression block 5", ctx)
            assertIs<Command.SetCompressionBlock>(cmd)
            assertEquals(5, cmd.size)
        }

        @Test
        @DisplayName("colon-compression status парсится как ShowCompressionStatus")
        fun `compression status parsed as ShowCompressionStatus`() {
            assertEquals(Command.ShowCompressionStatus, CommandParser.parse(":compression status", ctx))
        }

        @Test
        @DisplayName("colon-comp on парсится как SetCompressionEnabled")
        fun `comp on parsed as SetCompressionEnabled`() {
            val cmd = CommandParser.parse(":comp on", ctx)
            assertIs<Command.SetCompressionEnabled>(cmd)
            assertEquals(true, cmd.enabled)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Strategy команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Strategy команды")
    inner class StrategyCommands {

        private val ctx = taskContext(1)

        @Test
        @DisplayName("colon-strategy без аргументов = ShowStrategyMenu")
        fun `strategy without args equals ShowStrategyMenu`() {
            assertEquals(Command.ShowStrategyMenu, CommandParser.parse(":strategy", ctx))
        }

        @Test
        @DisplayName("colon-strategy info парсится как ShowCurrentStrategy")
        fun `strategy info parsed as ShowCurrentStrategy`() {
            assertEquals(Command.ShowCurrentStrategy, CommandParser.parse(":strategy info", ctx))
        }

        @Test
        @DisplayName("colon-strategy 1 парсится как SwitchStrategy")
        fun `strategy 1 parsed as SwitchStrategy`() {
            val cmd = CommandParser.parse(":strategy 1", ctx)
            assertIs<Command.SwitchStrategy>(cmd)
            assertEquals(1, cmd.index)
        }

        @Test
        @DisplayName("colon-branch create name парсится как CreateBranch")
        fun `branch create name parsed as CreateBranch`() {
            val cmd = CommandParser.parse(":branch create test-branch", ctx)
            assertIs<Command.CreateBranch>(cmd)
            assertEquals("test-branch", cmd.name)
        }

        @Test
        @DisplayName("colon-branch switch name парсится как SwitchBranch")
        fun `branch switch name parsed as SwitchBranch`() {
            val cmd = CommandParser.parse(":branch switch test-branch", ctx)
            assertIs<Command.SwitchBranch>(cmd)
            assertEquals("test-branch", cmd.name)
        }

        @Test
        @DisplayName("colon-branch list парсится как ListBranches")
        fun `branch list parsed as ListBranches`() {
            assertEquals(Command.ListBranches, CommandParser.parse(":branch list", ctx))
        }

        @Test
        @DisplayName("colon-checkpoint парсится как CreateCheckpoint")
        fun `checkpoint parsed as CreateCheckpoint`() {
            assertEquals(Command.CreateCheckpoint, CommandParser.parse(":checkpoint", ctx))
        }

        @Test
        @DisplayName("colon-checkpoint list парсится как ListCheckpoints")
        fun `checkpoint list parsed as ListCheckpoints`() {
            assertEquals(Command.ListCheckpoints, CommandParser.parse(":checkpoint list", ctx))
        }

        @Test
        @DisplayName("colon-facts парсится как ListFacts")
        fun `facts parsed as ListFacts`() {
            assertEquals(Command.ListFacts, CommandParser.parse(":facts", ctx))
        }

        @Test
        @DisplayName("colon-facts clear парсится как ClearFacts")
        fun `facts clear parsed as ClearFacts`() {
            assertEquals(Command.ClearFacts, CommandParser.parse(":facts clear", ctx))
        }

        @Test
        @DisplayName("colon-facts add key=value парсится как AddFact")
        fun `facts add key=value parsed as AddFact`() {
            val cmd = CommandParser.parse(":facts add key=value", ctx)
            assertIs<Command.AddFact>(cmd)
            assertEquals("key", cmd.key)
            assertEquals("value", cmd.value)
        }

        @Test
        @DisplayName("colon-facts remove key парсится как RemoveFact")
        fun `facts remove key parsed as RemoveFact`() {
            val cmd = CommandParser.parse(":facts remove key", ctx)
            assertIs<Command.RemoveFact>(cmd)
            assertEquals("key", cmd.key)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Memory команды (Phase 3)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Memory команды")
    inner class MemoryCommands {

        private val ctx = taskContext(1)

        @Test
        @DisplayName("colon-clear парсится как ClearMemory")
        fun `clear parsed as ClearMemory`() {
            assertEquals(Command.ClearMemory, CommandParser.parse(":clear", ctx))
        }

        @Test
        @DisplayName("colon-status парсится как ShowStatus")
        fun `status parsed as ShowStatus`() {
            assertEquals(Command.ShowStatus, CommandParser.parse(":status", ctx))
        }

        @Test
        @DisplayName("memory команды не чувствительны к регистру")
        fun `memory commands are case insensitive`() {
            assertEquals(Command.ClearMemory, CommandParser.parse(":CLEAR", ctx))
            assertEquals(Command.ShowStatus, CommandParser.parse(":STATUS", ctx))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Unknown команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unknown команды")
    inner class UnknownCommands {

        @Test
        @DisplayName("неизвестная команда парсится как Unknown")
        fun `unknown command parsed as Unknown`() {
            val cmd = CommandParser.parse(":unknown")
            assertIs<Command.Unknown>(cmd)
            assertEquals(":unknown", cmd.raw)
        }

        @Test
        @DisplayName("неизвестная команда с аргументами парсится как Unknown")
        fun `unknown command with args parsed as Unknown`() {
            val cmd = CommandParser.parse(":unknown arg1 arg2")
            assertIs<Command.Unknown>(cmd)
            assertEquals(":unknown arg1 arg2", cmd.raw)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Вспомогательные методы
    // ═══════════════════════════════════════════════════════════════

    private fun taskContext(taskId: Int): CommandContext {
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
}
