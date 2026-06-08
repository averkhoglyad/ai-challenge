package io.averkhogliad.ai.challenge.week0.cli.commands

import io.averkhogliad.ai.challenge.week0.domain.config.Task3Mode

/**
 * Контекст парсинга команд — определяет, какая задача активна
 * и какие команды доступны в текущем контексте.
 *
 * Контекст используется для различения команд, которые имеют
 * разный смысл в разных задачах (например, :models в Task5
 * показывает список моделей, а в других задачах — неизвестная команда).
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
            availableCommands = setOf("help", "h", "quit", "q", "task", "t")
        )

        /** Глобальные команды, доступные всегда */
        val GLOBAL_COMMANDS = setOf("help", "h", "quit", "q", "task", "t", "back", "b")
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
 * Некоторые команды доступны только в определённых задачах:
 * - :stop → только Task2
 * - :mode, :step, :meta, :role, :experts, :summary, :config → только Task3
 * - :models → Task5 (show models) vs Unknown (другие задачи)
 *
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
            "task", "t" -> Command.Back
            "back", "b" -> Command.Back

            // LLM параметры (Task2, Task4, Task5)
            "temp" -> {
                if (args.isEmpty()) {
                    Command.ShowParameters // Показать текущее значение
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
                    // :stop без аргументов = сброс стоп-последовательностей
                    Command.SetStopSequences(emptyList())
                } else {
                    val sequences = args.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    Command.SetStopSequences(sequences)
                }
            }

            "reset" -> Command.ResetParameters
            "params" -> Command.ShowParameters

            // Task3 команды
            "mode" -> {
                if (args.isEmpty()) {
                    Command.ShowConfig
                } else {
                    val mode = parseMode(args)
                    if (mode != null) Command.SetMode(mode)
                    else Command.Unknown(raw)
                }
            }

            "step" -> {
                if (args.isEmpty()) Command.ShowParameters
                else {
                    val enabled = parseOnOff(args)
                    if (enabled != null) Command.SetStep(enabled)
                    else Command.Unknown(raw)
                }
            }

            "meta" -> {
                if (args.isEmpty()) Command.ShowParameters
                else {
                    val enabled = parseOnOff(args)
                    if (enabled != null) Command.SetMeta(enabled)
                    else Command.Unknown(raw)
                }
            }

            "role" -> {
                if (args.isEmpty()) Command.ShowParameters
                else Command.SetRole(args)
            }

            "experts" -> {
                if (args.isEmpty()) {
                    Command.ShowParameters
                } else {
                    val expertsList = args.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    Command.SetExperts(expertsList)
                }
            }

            "summary" -> {
                if (args.isEmpty()) Command.ShowParameters
                else Command.ToggleSummary(args.lowercase() == "on")
            }

            "config" -> Command.ShowConfig

            // Task5 команды
            "models" -> {
                if (args.isEmpty()) {
                    Command.ShowModels
                } else {
                    val indices = args.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .mapNotNull { it.toIntOrNull() }
                    if (indices.isEmpty()) Command.Unknown(raw)
                    else Command.SetModels(indices)
                }
            }

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
            else -> Command.UserInput(input) // Нечисловой ввод на этапе выбора
        }
    }

    /**
     * Парсит строковое значение режима в [Task3Mode].
     * Допустимые строки: "direct", "experts" (регистронезависимо).
     * Возвращает null при неверном вводе.
     */
    internal fun parseMode(value: String): Task3Mode? {
        return when (value.lowercase()) {
            "direct" -> Task3Mode.DIRECT
            "experts" -> Task3Mode.EXPERTS
            else -> null
        }
    }

    /**
     * Парсит строковое значение "on"/"off" в Boolean.
     * Допустимые строки: "on", "off" (регистронезависимо).
     * Возвращает null при неверном вводе.
     */
    internal fun parseOnOff(value: String): Boolean? {
        return when (value.lowercase()) {
            "on" -> true
            "off" -> false
            else -> null
        }
    }

    /** Дефолтный промпт, если пользователь ничего не ввёл */
    const val DEFAULT_PROMPT = "Расскажи короткий анекдот про программиста."
}
