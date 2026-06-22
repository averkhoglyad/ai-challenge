package io.averkhogliad.ai.challenge.week2.cli.commands

import io.averkhogliad.ai.challenge.week2.domain.model.TaskId

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

            "list" -> Command.ListTasks
            "delete" -> {
                if (args.isEmpty()) Command.Unknown(raw)
                else Command.DeleteDialog(args)
            }

            "switch" -> {
                if (args.isEmpty()) Command.Unknown(raw)
                else Command.SwitchDialog(args)
            }

            "history" -> {
                Command.ShowHistory(args.ifEmpty { null })
            }

            // Команды управления сжатием контекста (для Task 4)
            "compression", "comp" -> parseCompressionCommand(args, raw)

            // Команды управления стратегиями контекста (для Task 5)
            "strategy" -> parseStrategyCommand(args, raw)
            "branch" -> parseBranchCommand(args, raw)
            "checkpoint" -> parseCheckpointCommand(args, raw)
            "facts" -> parseFactsCommand(args, raw)

            // Команды управления задачами todo-менеджера
            "add" -> parseAddTaskCommand(args, raw)
            "tasks" -> Command.ListTasks
            "edit" -> parseEditTaskCommand(args, raw)
            "drop" -> parseDropTaskCommand(args, raw)
            "open" -> parseOpenTaskCommand(args, raw)
            "close" -> parseCloseTaskCommand(args, raw)
            "cancel" -> parseCancelTaskCommand(args, raw)

            // Команды управления шагами задач
            "step-add" -> parseAddStepCommand(args, raw)
            "step-list" -> Command.ListSteps
            "step-done" -> parseCompleteStepCommand(args, raw)

            // Команды управления памятью (STM)
            "clear" -> Command.ClearMemory
            "status" -> Command.ShowStatus

            // Команды управления долговременной памятью (LTM)
            "ctx-save" -> parseSaveFactCommand(args, raw)
            "ctx-list" -> Command.ListLtmFacts
            "ctx-forget" -> parseForgetFactCommand(args, raw)
            "ctx-search" -> parseSearchFactsCommand(args, raw)

            // Команды интеграции с LLM
            "plan" -> parsePlanStepsCommand(args, raw)

            // Команды управления профилями пользователя (PM)
            "profile-new" -> parseProfileNewCommand(args, raw)
            "profile-list" -> Command.ProfileList
            "profile-use" -> parseProfileUseCommand(args, raw)
            "profile-edit" -> parseProfileEditCommand(args, raw)
            "profile-delete" -> parseProfileDeleteCommand(args, raw)
            "profile-show" -> parseProfileShowCommand(args, raw)

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
                if (index != null && index in 1..10) Command.SwitchStrategy(index)
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
     * Парсит ввод на этапе выбора задачи.
     * Числовой — SelectTask, 0 — Quit, нечисловой — Unknown (не отправляется в LLM).
     */
    internal fun parseTaskSelectionInput(input: String): Command {
        val number = input.toIntOrNull()
        return when {
            number == 0 -> Command.Quit
            number != null && number >= 1 -> Command.SelectTask(number)
            else -> Command.Unknown(input)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Парсинг команд управления задачами todo-менеджера
    // ═══════════════════════════════════════════════════════════════

    /**
     * Парсит команду добавления задачи: `:add <title>`.
     * Весь текст после `:add ` считается заголовком задачи.
     */
    internal fun parseAddTaskCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.AddTask(args)
        }
    }

    /**
     * Парсит команду редактирования задачи: `:edit <id> <title>` или `:edit <title>`.
     * Если первый токен похож на ID (число или UUID), то это ID,
     * иначе это контекстная команда без ID.
     */
    internal fun parseEditTaskCommand(args: String, raw: String): Command {
        if (args.isEmpty()) return Command.Unknown(raw)

        val parts = args.split(" ", limit = 2)
        val firstToken = parts[0]
        val rest = parts.getOrElse(1) { "" }.trim()

        return if (looksLikeId(firstToken) && rest.isNotEmpty()) {
            // Первый токен похож на ID и есть второй токен — это ID и title
            Command.EditTask(TaskId(firstToken), rest)
        } else {
            // Иначе это контекстная команда (весь args — это title)
            Command.EditTask(null, args)
        }
    }

    /**
     * Парсит команду удаления задачи: `:drop <id>` или `:drop`.
     * Если аргумент пустой, это контекстная команда (id=null).
     */
    internal fun parseDropTaskCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.DropTask(null)
        } else {
            Command.DropTask(TaskId(args))
        }
    }

    /**
     * Парсит команду открытия задачи: `:open <id>`.
     * ID обязателен.
     */
    internal fun parseOpenTaskCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.OpenTask(TaskId(args))
        }
    }

    /**
     * Парсит команду закрытия задачи: `:close <id>` или `:close`.
     * Если аргумент пустой, это контекстная команда (id=null).
     */
    internal fun parseCloseTaskCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.CloseTask(null)
        } else {
            Command.CloseTask(TaskId(args))
        }
    }

    /**
     * Парсит команду отмены задачи: `:cancel <id>` или `:cancel`.
     * Если аргумент пустой, это контекстная команда (id=null).
     */
    internal fun parseCancelTaskCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.CancelTask(null)
        } else {
            Command.CancelTask(TaskId(args))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Парсинг команд управления шагами задач
    // ═══════════════════════════════════════════════════════════════

    /**
     * Парсит команду добавления шага: `:step-add <text>`.
     * Весь текст после `:step-add ` считается описанием шага.
     * Текст обязателен.
     */
    internal fun parseAddStepCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.AddStep(args)
        }
    }

    /**
     * Парсит команду отметки шага выполненным: `:step-done <id>`.
     * ID шага обязателен.
     */
    internal fun parseCompleteStepCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.CompleteStep(args)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Парсинг команд управления долговременной памятью (LTM)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Парсит команду сохранения факта: `:ctx-save <content>`.
     * Весь текст после `:ctx-save ` считается содержимым факта.
     * Содержимое обязательно.
     */
    internal fun parseSaveFactCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.SaveFact(args)
        }
    }

    /**
     * Парсит команду удаления факта: `:ctx-forget <factId>`.
     * ID факта обязателен.
     */
    internal fun parseForgetFactCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.ForgetFact(args)
        }
    }

    /**
     * Парсит команду поиска фактов: `:ctx-search <query>`.
     * Поисковый запрос обязателен.
     */
    internal fun parseSearchFactsCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.SearchFacts(args)
        }
    }

    /**
     * Парсит команду планирования шагов: `:plan <title> [description]`.
     * Весь текст после `:plan ` считается заголовком и опциональным описанием,
     * разделёнными пробелом.
     */
    internal fun parsePlanStepsCommand(args: String, raw: String): Command {
        if (args.isEmpty()) return Command.Unknown(raw)
        val parts = args.split(" ", limit = 2)
        val title = parts[0].trim()
        val description = parts.getOrElse(1) { "" }.trim().ifEmpty { null }
        return Command.PlanSteps(title, description)
    }

    // ═══════════════════════════════════════════════════════════════
    // Парсинг команд управления профилями пользователя (PM)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Парсит команду создания профиля: `:profile-new <name>`.
     * Название обязательно.
     */
    internal fun parseProfileNewCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.ProfileNew(args)
        }
    }

    /**
     * Парсит команду активации профиля: `:profile-use <name>`.
     * Имя обязательно. `none` разрешён — будет обработан как деактивация профиля.
     */
    internal fun parseProfileUseCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.ProfileUse(args)
        }
    }

    /**
     * Парсит команду редактирования профиля: `:profile-edit <name>`.
     * Название обязательно.
     */
    internal fun parseProfileEditCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.ProfileEdit(args)
        }
    }

    /**
     * Парсит команду удаления профиля: `:profile-delete <name>`.
     * Название обязательно.
     */
    internal fun parseProfileDeleteCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.ProfileDelete(args)
        }
    }

    /**
     * Парсит команду просмотра профиля: `:profile-show [name]`.
     * Название опционально (без названия — показать активный профиль).
     */
    internal fun parseProfileShowCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.ProfileShow(null)
        } else {
            Command.ProfileShow(args)
        }
    }

    /**
     * Проверяет, похож ли токен на ID задачи.
     * ID может быть числом или строкой, похожей на UUID.
     */
    private fun looksLikeId(token: String): Boolean {
        if (token.isEmpty()) return false
        // Число
        if (token.toIntOrNull() != null) return true
        // UUID-подобная строка (содержит дефисы и hex-символы)
        if (token.matches(Regex("[0-9a-fA-F-]+")) && token.contains("-")) return true
        // Строка, состоящая только из hex-символов (без пробелов и не слов)
        if (token.matches(Regex("[0-9a-fA-F]+")) && token.length >= 8) return true
        return false
    }

    /** Дефолтный промпт, если пользователь ничего не ввёл */
    const val DEFAULT_PROMPT = "Расскажи короткий анекдот про программиста."
}
