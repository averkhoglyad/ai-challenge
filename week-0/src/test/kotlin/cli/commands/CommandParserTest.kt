package io.averkhogliad.ai.challenge.week0.cli.commands

import io.averkhogliad.ai.challenge.week0.domain.config.Task3Mode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
            assertEquals(Command.Help, CommandParser.parse(":help", taskContext(2)))
            assertEquals(Command.Help, CommandParser.parse(":h"))
        }

        @Test
        @DisplayName("colon-quit парсится как Quit")
        fun `quit parsed as Quit`() {
            assertEquals(Command.Quit, CommandParser.parse(":quit"))
            assertEquals(Command.Quit, CommandParser.parse(":q"))
        }

        @Test
        @DisplayName("colon-task парсится как Back")
        fun `task parsed as Back`() {
            assertEquals(Command.Back, CommandParser.parse(":task", taskContext(2)))
            assertEquals(Command.Back, CommandParser.parse(":t", taskContext(2)))
        }

        @Test
        @DisplayName("colon-back парсится как Back")
        fun `back parsed as Back`() {
            assertEquals(Command.Back, CommandParser.parse(":back", taskContext(2)))
            assertEquals(Command.Back, CommandParser.parse(":b", taskContext(2)))
        }

        @Test
        @DisplayName("глобальные команды не чувствительны к регистру")
        fun `global commands are case insensitive`() {
            assertEquals(Command.Help, CommandParser.parse(":HELP"))
            assertEquals(Command.Quit, CommandParser.parse(":QUIT"))
            assertEquals(Command.Back, CommandParser.parse(":TASK"))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Пользовательский ввод
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Пользовательский ввод")
    inner class UserInput {

        @Test
        @DisplayName("пустой ввод даёт дефолтный промпт")
        fun `empty input gives default prompt`() {
            val cmd = CommandParser.parse("")
            assertIs<Command.UserInput>(cmd)
            assertEquals("Расскажи короткий анекдот про программиста.", cmd.text)
        }

        @Test
        @DisplayName("обычный текст становится UserInput в контексте задачи")
        fun `plain text becomes UserInput in task context`() {
            val cmd = CommandParser.parse("Привет, как дела?", taskContext(2))
            assertIs<Command.UserInput>(cmd)
            assertEquals("Привет, как дела?", cmd.text)
        }

        @Test
        @DisplayName("пробельный ввод считается пустым и даёт дефолтный промпт")
        fun `whitespace input is empty and gives default prompt`() {
            val cmd = CommandParser.parse("   ")
            assertIs<Command.UserInput>(cmd)
            assertEquals("Расскажи короткий анекдот про программиста.", cmd.text)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Выбор задачи
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Выбор задачи (контекст без активной задачи)")
    inner class TaskSelection {

        @Test
        @DisplayName("числовой ввод 1-5 парсится как SelectTask")
        fun `numeric input 1-5 parsed as SelectTask`() {
            assertEquals(Command.SelectTask(1), CommandParser.parse("1"))
            assertEquals(Command.SelectTask(5), CommandParser.parse("5"))
        }

        @Test
        @DisplayName("0 парсится как Quit при выборе задачи")
        fun `zero parsed as Quit in task selection`() {
            assertEquals(Command.Quit, CommandParser.parse("0"))
        }

        @Test
        @DisplayName("нечисловой ввод при выборе задачи становится UserInput")
        fun `non-numeric input in selection becomes UserInput`() {
            val cmd = CommandParser.parse("hello")
            assertIs<Command.UserInput>(cmd)
            assertEquals("hello", cmd.text)
        }

        @Test
        @DisplayName("отрицательное число становится UserInput")
        fun `negative number becomes UserInput`() {
            val cmd = CommandParser.parse("-1")
            assertIs<Command.UserInput>(cmd)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Task2 команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Task2 команды")
    inner class Task2Commands {

        private val ctx = taskContext(2)

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
    // Task3 команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Task3 команды")
    inner class Task3Commands {

        private val ctx = taskContext(3)

        @Test
        @DisplayName("colon-mode direct парсится как SetMode")
        fun `mode direct parsed as SetMode`() {
            val cmd = CommandParser.parse(":mode direct", ctx)
            assertIs<Command.SetMode>(cmd)
            assertEquals(Task3Mode.DIRECT, cmd.mode)
        }

        @Test
        @DisplayName("colon-mode experts парсится как SetMode")
        fun `mode experts parsed as SetMode`() {
            val cmd = CommandParser.parse(":mode experts", ctx)
            assertIs<Command.SetMode>(cmd)
            assertEquals(Task3Mode.EXPERTS, cmd.mode)
        }

        @Test
        @DisplayName("colon-mode без аргументов = ShowConfig")
        fun `mode without args equals ShowConfig`() {
            assertEquals(Command.ShowConfig, CommandParser.parse(":mode", ctx))
        }

        @Test
        @DisplayName("colon-mode с неверным значением = Unknown")
        fun `mode with invalid value equals Unknown`() {
            val cmd = CommandParser.parse(":mode invalid", ctx)
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("colon-step on парсится как SetStep")
        fun `step on parsed as SetStep`() {
            val cmd = CommandParser.parse(":step on", ctx)
            assertIs<Command.SetStep>(cmd)
            assertEquals(true, cmd.enabled)
        }

        @Test
        @DisplayName("colon-step off парсится как SetStep")
        fun `step off parsed as SetStep`() {
            val cmd = CommandParser.parse(":step off", ctx)
            assertIs<Command.SetStep>(cmd)
            assertEquals(false, cmd.enabled)
        }

        @Test
        @DisplayName("colon-step без аргументов = ShowParameters")
        fun `step without args equals ShowParameters`() {
            assertEquals(Command.ShowParameters, CommandParser.parse(":step", ctx))
        }

        @Test
        @DisplayName("colon-step с неверным значением = Unknown")
        fun `step with invalid value equals Unknown`() {
            val cmd = CommandParser.parse(":step invalid", ctx)
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("colon-meta on парсится как SetMeta")
        fun `meta on parsed as SetMeta`() {
            val cmd = CommandParser.parse(":meta on", ctx)
            assertIs<Command.SetMeta>(cmd)
            assertEquals(true, cmd.enabled)
        }

        @Test
        @DisplayName("colon-meta off парсится как SetMeta")
        fun `meta off parsed as SetMeta`() {
            val cmd = CommandParser.parse(":meta off", ctx)
            assertIs<Command.SetMeta>(cmd)
            assertEquals(false, cmd.enabled)
        }

        @Test
        @DisplayName("colon-meta без аргументов = ShowParameters")
        fun `meta without args equals ShowParameters`() {
            assertEquals(Command.ShowParameters, CommandParser.parse(":meta", ctx))
        }

        @Test
        @DisplayName("colon-meta с неверным значением = Unknown")
        fun `meta with invalid value equals Unknown`() {
            val cmd = CommandParser.parse(":meta invalid", ctx)
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("parseMode возвращает корректные значения")
        fun `parseMode returns correct values`() {
            assertEquals(Task3Mode.DIRECT, CommandParser.parseMode("direct"))
            assertEquals(Task3Mode.DIRECT, CommandParser.parseMode("DIRECT"))
            assertEquals(Task3Mode.EXPERTS, CommandParser.parseMode("experts"))
            assertEquals(Task3Mode.EXPERTS, CommandParser.parseMode("EXPERTS"))
            assertEquals(null, CommandParser.parseMode("invalid"))
            assertEquals(null, CommandParser.parseMode(""))
        }

        @Test
        @DisplayName("parseOnOff возвращает корректные значения")
        fun `parseOnOff returns correct values`() {
            assertEquals(true, CommandParser.parseOnOff("on"))
            assertEquals(true, CommandParser.parseOnOff("ON"))
            assertEquals(false, CommandParser.parseOnOff("off"))
            assertEquals(false, CommandParser.parseOnOff("OFF"))
            assertEquals(null, CommandParser.parseOnOff("invalid"))
            assertEquals(null, CommandParser.parseOnOff(""))
        }

        @Test
        @DisplayName("colon-role с текстом парсится как SetRole")
        fun `role with text parsed as SetRole`() {
            val cmd = CommandParser.parse(":role Эксперт по Kotlin", ctx)
            assertIs<Command.SetRole>(cmd)
            assertEquals("Эксперт по Kotlin", cmd.role)
        }

        @Test
        @DisplayName("colon-role без аргументов = ShowParameters")
        fun `role without args equals ShowParameters`() {
            assertEquals(Command.ShowParameters, CommandParser.parse(":role", ctx))
        }

        @Test
        @DisplayName("colon-experts с двумя значениями парсится как SetExperts")
        fun `experts with two values parsed as SetExperts`() {
            val cmd = CommandParser.parse(":experts Архитектор,Разработчик", ctx)
            assertIs<Command.SetExperts>(cmd)
            assertEquals(listOf("Архитектор", "Разработчик"), cmd.experts)
        }

        @Test
        @DisplayName("colon-experts без аргументов = ShowParameters")
        fun `experts without args equals ShowParameters`() {
            assertEquals(Command.ShowParameters, CommandParser.parse(":experts", ctx))
        }

        @Test
        @DisplayName("colon-summary on парсится как ToggleSummary")
        fun `summary on parsed as ToggleSummary`() {
            val cmd = CommandParser.parse(":summary on", ctx)
            assertIs<Command.ToggleSummary>(cmd)
            assertEquals(true, cmd.value)
        }

        @Test
        @DisplayName("colon-summary off парсится как ToggleSummary")
        fun `summary off parsed as ToggleSummary`() {
            val cmd = CommandParser.parse(":summary off", ctx)
            assertIs<Command.ToggleSummary>(cmd)
            assertEquals(false, cmd.value)
        }

        @Test
        @DisplayName("colon-summary без аргументов = ShowParameters")
        fun `summary without args equals ShowParameters`() {
            assertEquals(Command.ShowParameters, CommandParser.parse(":summary", ctx))
        }

        @Test
        @DisplayName("colon-config парсится как ShowConfig")
        fun `config parsed as ShowConfig`() {
            assertEquals(Command.ShowConfig, CommandParser.parse(":config", ctx))
        }

        @Test
        @DisplayName("colon-reset парсится как ResetParameters в контексте Task3")
        fun `reset parsed as ResetParameters in Task3 context`() {
            assertEquals(Command.ResetParameters, CommandParser.parse(":reset", ctx))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Task4 команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Task4 команды")
    inner class Task4Commands {

        private val ctx = taskContext(4)

        @Test
        @DisplayName("colon-temp с запятой (список) = Unknown для одиночного SetTemperature")
        fun `temp with comma list equals Unknown`() {
            val cmd = CommandParser.parse(":temp 0.5,0.8", ctx)
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("colon-temp 0.5 парсится как SetTemperature")
        fun `temp 0-5 parsed as SetTemperature`() {
            val cmd = CommandParser.parse(":temp 0.5", ctx)
            assertIs<Command.SetTemperature>(cmd)
            assertEquals(0.5, cmd.value)
        }

        @Test
        @DisplayName("colon-maxTokens 1000 парсится как SetMaxTokens")
        fun `maxTokens 1000 parsed as SetMaxTokens`() {
            val cmd = CommandParser.parse(":maxTokens 1000", ctx)
            assertIs<Command.SetMaxTokens>(cmd)
            assertEquals(1000, cmd.value)
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
    // Task5 команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Task5 команды")
    inner class Task5Commands {

        private val ctx = taskContext(5)

        @Test
        @DisplayName("colon-models без аргументов = ShowModels")
        fun `models without args equals ShowModels`() {
            assertEquals(Command.ShowModels, CommandParser.parse(":models", ctx))
        }

        @Test
        @DisplayName("colon-models 1,3 парсится как SetModels с индексами")
        fun `models 1,3 parsed as SetModels`() {
            val cmd = CommandParser.parse(":models 1,3", ctx)
            assertIs<Command.SetModels>(cmd)
            assertEquals(listOf(1, 3), cmd.modelIndices)
        }

        @Test
        @DisplayName("colon-models 1 парсится как SetModels с одним индексом")
        fun `models 1 parsed as SetModels single index`() {
            val cmd = CommandParser.parse(":models 1", ctx)
            assertIs<Command.SetModels>(cmd)
            assertEquals(listOf(1), cmd.modelIndices)
        }

        @Test
        @DisplayName("colon-models abc = Unknown (невалидные индексы)")
        fun `models abc equals Unknown`() {
            val cmd = CommandParser.parse(":models abc", ctx)
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("colon-maxTokens 500 парсится как SetMaxTokens")
        fun `maxTokens 500 parsed as SetMaxTokens`() {
            val cmd = CommandParser.parse(":maxTokens 500", ctx)
            assertIs<Command.SetMaxTokens>(cmd)
            assertEquals(500, cmd.value)
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
    // Неизвестные команды
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Неизвестные команды")
    inner class UnknownCommands {

        @Test
        @DisplayName("неизвестная команда становится Unknown")
        fun `unknown command becomes Unknown`() {
            val cmd = CommandParser.parse(":unknown", taskContext(2))
            assertIs<Command.Unknown>(cmd)
            assertEquals(":unknown", cmd.raw)
        }

        @Test
        @DisplayName("команда colon-temp в неверном контексте (Task1) = Unknown")
        fun `temp in wrong context Task1 equals Unknown`() {
            val cmd = CommandParser.parse(":temp 0.7", taskContext(1))
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("colon-models в неверном контексте (Task2) = Unknown")
        fun `models in wrong context Task2 equals Unknown`() {
            val cmd = CommandParser.parse(":models", taskContext(2))
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("colon-mode в неверном контексте (Task2) = Unknown")
        fun `mode in wrong context Task2 equals Unknown`() {
            val cmd = CommandParser.parse(":mode direct", taskContext(2))
            assertIs<Command.Unknown>(cmd)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Контекстно-зависимый парсинг
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Контекстно-зависимый парсинг")
    inner class ContextDependent {

        @Test
        @DisplayName("colon-models в Task5 = ShowModels, в Task2 = Unknown")
        fun `models Task5 vs Task2 context dependent`() {
            assertEquals(Command.ShowModels, CommandParser.parse(":models", taskContext(5)))
            assertIs<Command.Unknown>(CommandParser.parse(":models", taskContext(2)))
        }

        @Test
        @DisplayName("colon-config в Task3 = ShowConfig, в Task2 = Unknown")
        fun `config Task3 vs Task2 context dependent`() {
            assertEquals(Command.ShowConfig, CommandParser.parse(":config", taskContext(3)))
            assertIs<Command.Unknown>(CommandParser.parse(":config", taskContext(2)))
        }

        @Test
        @DisplayName("colon-stop в Task2 = SetStopSequences, в Task4 = Unknown")
        fun `stop Task2 vs Task4 context dependent`() {
            assertIs<Command.SetStopSequences>(CommandParser.parse(":stop END", taskContext(2)))
            assertIs<Command.Unknown>(CommandParser.parse(":stop END", taskContext(4)))
        }

        @Test
        @DisplayName("colon-temp работает в Task2 и Task4")
        fun `temp works in Task2 and Task4`() {
            assertIs<Command.SetTemperature>(CommandParser.parse(":temp 0.7", taskContext(2)))
            assertIs<Command.SetTemperature>(CommandParser.parse(":temp 0.7", taskContext(4)))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Граничные случаи
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Граничные случаи")
    inner class EdgeCases {

        @Test
        @DisplayName("пустая строка после двоеточия = Unknown")
        fun `empty string after colon equals Unknown`() {
            val cmd = CommandParser.parse(":", taskContext(2))
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("двоеточие с пробелом = Unknown")
        fun `colon with space equals Unknown`() {
            val cmd = CommandParser.parse(": ", taskContext(2))
            assertIs<Command.Unknown>(cmd)
        }

        @Test
        @DisplayName("команда с лишними пробелами")
        fun `command with extra spaces`() {
            val cmd = CommandParser.parse(":temp   0.7", taskContext(2))
            assertIs<Command.SetTemperature>(cmd)
            assertEquals(0.7, cmd.value)
        }

        @Test
        @DisplayName("команда stop с пробелами в списке")
        fun `stop command with spaces in list`() {
            val cmd = CommandParser.parse(":stop  END , DONE ", taskContext(2))
            assertIs<Command.SetStopSequences>(cmd)
            assertEquals(listOf("END", "DONE"), cmd.values)
        }

        @Test
        @DisplayName("команда models с пробелами в индексах")
        fun `models command with spaces in indices`() {
            val cmd = CommandParser.parse(":models  1 , 3 ", taskContext(5))
            assertIs<Command.SetModels>(cmd)
            assertEquals(listOf(1, 3), cmd.modelIndices)
        }

        @Test
        @DisplayName("температура 0.0 парсится корректно")
        fun `temperature 0-0 parsed correctly`() {
            val cmd = CommandParser.parse(":temp 0.0", taskContext(2))
            assertIs<Command.SetTemperature>(cmd)
            assertEquals(0.0, cmd.value)
        }

        @Test
        @DisplayName("температура 2.0 парсится корректно")
        fun `temperature 2-0 parsed correctly`() {
            val cmd = CommandParser.parse(":temp 2.0", taskContext(2))
            assertIs<Command.SetTemperature>(cmd)
            assertEquals(2.0, cmd.value)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Вспомогательные методы
    // ═══════════════════════════════════════════════════════════════

    companion object {
        /**
         * Создаёт контекст для задачи с полным набором команд.
         */
        private fun taskContext(taskId: Int): CommandContext {
            val commands = when (taskId) {
                1 -> setOf() // Task1 не имеет специфичных команд
                2 -> setOf("temp", "maxtokens", "stop", "reset", "params")
                3 -> setOf("mode", "step", "meta", "role", "experts", "summary", "config", "reset", "params")
                4 -> setOf("temp", "maxtokens", "reset", "params")
                5 -> setOf("models", "maxtokens", "reset", "params")
                else -> setOf("help", "quit", "back")
            }
            return CommandContext(
                currentTaskId = taskId,
                availableCommands = commands + CommandContext.GLOBAL_COMMANDS
            )
        }
    }
}
