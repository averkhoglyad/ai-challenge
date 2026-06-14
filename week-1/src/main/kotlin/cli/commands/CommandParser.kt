package io.averkhogliad.ai.challenge.week1.cli.commands

import io.averkhogliad.ai.challenge.week1.domain.strategy.StrategyType

/**
 * Контекст парсинга команд — определяет, какая задача активна
 * и какие команды доступны в текущем контексте.
 *
 * Контекст используется для различения команд, которые имеют
 * разный смысл в разных задачах.
 */
data class CommandContext(
    /** ID активной задачи (null — выбор задачи) */
    val currentTaskId: Int?,
    /** Множество доступных команд в текущем контексте */
    val availableCommands: Set<String>
) {
    companion object {
        /** Контекст для этапа выбора задачи */
        val TASK_SELECTION = CommandContext(
            currentTaskId = null,
            availableCommands = setOf("help", "h", "quit", "q", "back", "b")
        )

        /** Глобальные команды, доступные всегда */
        val GLOBAL_COMMANDS = setOf("help", "h", "quit", "q", "back", "b")
    }
}

/**
 * Парсер пользовательского ввода в типизированные команды.
 *
 * Чистая функция без побочных эффектов: String + CommandContext → Command.
 * Не зависит от UI (Mordant, Terminal) и от executors.
 *
 * ## Стратегия парсинга:
 * 1. Пустой ввод → UserInput с дефолтным промптом
 * 2. Ввод начинается с ':' → парсинг команды
 * 3. Ввод не начинается с ':' → числовой ввод (выбор задачи) или UserInput
 *
 * ## Контекстный парсинг:
 * Команды доступны согласно availableCommands в CommandContext.
 * Если команда недоступна в текущем контексте, возвращается Unknown.
 */
object CommandParser {

    /**
     * Парсит строковый ввод пользователя в типизированную команду.
     *
     * @param input Сырой ввод пользователя (trimmed)
     * @param context Контекст (какая задача активна, какие команды доступны)
     * @return Типизированная команда
     */
    fun parse(input: String, context: CommandContext = CommandContext.TASK_SELECTION): Command {
        val trimmed = input.trim()
        return when {
            // Пустой ввод → дефолтный промпт
            trimmed.isEmpty() -> Command.UserInput(DEFAULT_PROMPT)

            // Команды (начинаются с ':')
            trimmed.startsWith(":") -> parseCommand(trimmed, context)

            // Числовой ввод на этапе выбора задачи
            context.currentTaskId == null -> parseTaskSelectionInput(trimmed)

            // Обычный текст → промпт пользователя
            else -> Command.UserInput(trimmed)
        }
    }

    /**
     * Парсит команду вида `:command [args]`.
     */
    internal fun parseCommand(input: String, context: CommandContext): Command {
        val parts = input.split(" ", limit = 2)
        val commandName = parts[0].removePrefix(":").lowercase()
        val args = parts.getOrElse(1) { "" }.trim()

        // Проверяем доступность команды в текущем контексте
        if (commandName !in context.availableCommands && commandName !in CommandContext.GLOBAL_COMMANDS) {
            return Command.Unknown(input)
        }

        return parseCommandWithArgs(commandName, args, input)
    }

    /**
     * Парсит команду с аргументами.
     */
    internal fun parseCommandWithArgs(commandName: String, args: String, raw: String): Command {
        return when (commandName) {
            // Глобальные команды
            "help", "h" -> Command.Help
            "quit", "q" -> Command.Quit
            "back", "b" -> Command.Back

            // LLM параметры
            "temp" -> {
                if (args.isEmpty()) {
                    Command.ShowParameters
                } else {
                    val value = args.toDoubleOrNull()
                    if (value != null) Command.SetTemperature(value)
                    else Command.Unknown(raw)
                }
            }

            "maxtokens" -> {
                if (args.isEmpty()) {
                    Command.ShowParameters
                } else {
                    val value = args.toIntOrNull()
                    if (value != null) Command.SetMaxTokens(value)
                    else Command.Unknown(raw)
                }
            }

            "stop" -> {
                if (args.isEmpty()) {
                    Command.SetStopSequences(emptyList())
                } else {
                    val sequences = args.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    Command.SetStopSequences(sequences)
                }
            }

            "reset" -> Command.ResetParameters
            "params" -> Command.ShowParameters

            // Команды управления диалогами (для Task 2)
            "new" -> {
                val title = args.ifEmpty { "New Dialog" }
                Command.NewDialog(title)
            }

            "list" -> Command.ListDialogs
            "delete" -> {
                if (args.isEmpty()) Command.Unknown(raw)
                else Command.DeleteDialog(args)
            }

            "switch" -> {
                if (args.isEmpty()) Command.Unknown(raw)
                else Command.SwitchDialog(args)
            }

            // Команды управления сжатием контекста (для Task 4)
            "compression", "comp" -> parseCompressionCommand(args, raw)

            // Команды управления стратегиями контекста (для Task 5)
            "strategy" -> parseStrategyCommand(args, raw)
            "branch" -> parseBranchCommand(args, raw)
            "checkpoint" -> parseCheckpointCommand(args, raw)
            "facts" -> parseFactsCommand(args, raw)

            else -> Command.Unknown(raw)
        }
    }

    /**
     * Парсит команды стратегий: `:strategy [index]`.
     */
    internal fun parseStrategyCommand(args: String, raw: String): Command {
        return when {
            args.isEmpty() -> Command.ShowStrategyMenu
            args == "info" -> Command.ShowCurrentStrategy
            else -> {
                val index = args.toIntOrNull()
                if (index != null && index in 1..StrategyType.entries.size) Command.SwitchStrategy(index)
                else Command.Unknown(raw)
            }
        }
    }

    /**
     * Парсит команды веток: `:branch create <name>`, `:branch switch <name>`, `:branch list`.
     */
    internal fun parseBranchCommand(args: String, raw: String): Command {
        val parts = args.split(" ", limit = 2)
        val subCommand = parts[0].lowercase()
        val subArgs = parts.getOrElse(1) { "" }.trim()

        return when (subCommand) {
            "create" -> {
                if (subArgs.isEmpty()) Command.Unknown(raw)
                else Command.CreateBranch(subArgs)
            }

            "switch" -> {
                if (subArgs.isEmpty()) Command.Unknown(raw)
                else Command.SwitchBranch(subArgs)
            }

            "list" -> Command.ListBranches
            else -> Command.Unknown(raw)
        }
    }

    /**
     * Парсит команды чекпоинтов: `:checkpoint`, `:checkpoint list`.
     */
    internal fun parseCheckpointCommand(args: String, raw: String): Command {
        return when (args.lowercase()) {
            "", "create" -> Command.CreateCheckpoint
            "list" -> Command.ListCheckpoints
            else -> Command.Unknown(raw)
        }
    }

    /**
     * Парсит команды фактов: `:facts`, `:facts clear`, `:facts add <key>=<value>`, `:facts remove <key>`.
     */
    internal fun parseFactsCommand(args: String, raw: String): Command {
        val parts = args.split(" ", limit = 2)
        val subCommand = parts[0].lowercase()
        val subArgs = parts.getOrElse(1) { "" }.trim()

        return when (subCommand) {
            "" -> Command.ListFacts
            "clear" -> Command.ClearFacts
            "add" -> {
                val eqIndex = subArgs.indexOf('=')
                if (eqIndex > 0 && eqIndex < subArgs.length - 1) {
                    val key = subArgs.substring(0, eqIndex).trim()
                    val value = subArgs.substring(eqIndex + 1).trim()
                    Command.AddFact(key, value)
                } else {
                    Command.Unknown(raw)
                }
            }

            "remove" -> {
                if (subArgs.isEmpty()) Command.Unknown(raw)
                else Command.RemoveFact(subArgs)
            }
            else -> Command.Unknown(raw)
        }
    }

    /**
     * Парсит команды сжатия контекста: `:compression on/off/window <N>/block <K>/status`.
     */
    internal fun parseCompressionCommand(args: String, raw: String): Command {
        val parts = args.split(" ", limit = 2)
        val subCommand = parts[0].lowercase()
        val subArgs = parts.getOrElse(1) { "" }.trim()

        return when (subCommand) {
            "on" -> Command.SetCompressionEnabled(true)
            "off" -> Command.SetCompressionEnabled(false)
            "window" -> {
                val size = subArgs.toIntOrNull()
                if (size != null && size > 0) Command.SetCompressionWindow(size)
                else Command.Unknown(raw)
            }

            "block" -> {
                val size = subArgs.toIntOrNull()
                if (size != null && size > 0) Command.SetCompressionBlock(size)
                else Command.Unknown(raw)
            }

            "status" -> Command.ShowCompressionStatus
            else -> Command.Unknown(raw)
        }
    }

    /**
     * Парсит ввод на этапе выбора задачи (нечисловой становится UserInput,
     * числовой — SelectTask, 0 — Quit).
     */
    internal fun parseTaskSelectionInput(input: String): Command {
        val number = input.toIntOrNull()
        return when {
            number == 0 -> Command.Quit
            number != null && number >= 1 -> Command.SelectTask(number)
            else -> Command.UserInput(input)
        }
    }

    /** Дефолтный промпт, если пользователь ничего не ввёл */
    const val DEFAULT_PROMPT = "Расскажи короткий анекдот про программиста."
}
