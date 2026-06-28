package io.averkhogliad.ai.challenge.week3.cli.cli.commands

import io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugAction
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTransport
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId

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
            availableCommands = setOf("help", "h", "quit", "q", "back", "b", "debug")
        )

        /** Глобальные команды, доступные всегда. */
        val GLOBAL_COMMANDS = setOf(
            "help",
            "h",
            "quit",
            "q",
            "back",
            "b",
            "debug",
            "state",
            "abort",
            "mcp-add",
            "mcp-list",
            "mcp-remove",
            "mcp-connect",
            "mcp-disconnect",
            "mcp-tools",
            "notes"
        )

        /**
         * Command roots routed by CliCommandDispatcher in a normal active CLI context.
         * Keep this list in sync with dispatcher routes so parser availability does not hide commands.
         */
        val ACTIVE_COMMANDS = setOf(
            "help", "h", "quit", "q", "back", "b",
            "new", "delete", "switch", "history",
            "add", "list", "tasks", "edit", "drop", "open", "close", "cancel",

            "step-add", "step-list", "step-done",
            "temp", "maxtokens", "reset", "params", "stop",
            "plan",
            "debug", "state", "abort", "goto",
            "status", "clear", "ctx-save", "ctx-list", "ctx-forget", "ctx-search",
            "profile-new", "profile-list", "profile-use", "profile-edit", "profile-delete", "profile-show",
            "mcp-add", "mcp-list", "mcp-remove", "mcp-connect", "mcp-disconnect", "mcp-tools",
            "invariant",
            "compression", "comp",
            "strategy", "branch", "checkpoint", "facts",
            "create-event", "notes"
        )

        fun activeTaskContext(taskId: Int = 1): CommandContext = CommandContext(
            currentTaskId = taskId,
            availableCommands = ACTIVE_COMMANDS
        )

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
            // Пустой ввод — игнорируем, не создаём промпт
            trimmed.isEmpty() -> Command.UserInput("")

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

            // Команды управления диалогами
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

            // Команды управления сжатием контекста
            "compression", "comp" -> parseCompressionCommand(args, raw)

            // Команды управления стратегиями контекста (для Task 1)
            "strategy" -> parseStrategyCommand(args, raw)
            "branch" -> parseBranchCommand(args, raw)
            "checkpoint" -> parseCheckpointCommand(args, raw)
            "facts" -> parseFactsCommand(args, raw)

            // Команды управления задачами todo-менеджера
            "add" -> parseAddTaskCommand(args, raw)
            "tasks" -> Command.ListTasks
            "edit" -> parseEditTaskCommand(args, raw)
            "drop" -> parseDropTaskCommand(args)
            "open" -> parseOpenTaskCommand(args, raw)
            "close" -> parseCloseTaskCommand(args)
            "cancel" -> parseCancelTaskCommand(args)

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
            "plan" -> parsePlanCommand(args)

            // Команды управления debug-режимом
            "debug" -> parseDebugCommand(args, raw)

            // Команды управления состоянием FSM
            "state" -> Command.ShowState
            "abort" -> Command.Abort
            "goto" -> parseGotoCommand(args)

            // Команды управления профилями пользователя (PM)
            "profile-new" -> parseProfileNewCommand(args, raw)
            "profile-list" -> Command.ProfileList
            "profile-use" -> parseProfileUseCommand(args, raw)
            "profile-edit" -> parseProfileEditCommand(args, raw)
            "profile-delete" -> parseProfileDeleteCommand(args, raw)
            "profile-show" -> parseProfileShowCommand(args)

            // Команды управления MCP-серверами
            "mcp-add" -> parseMcpAddCommand(args, raw)
            "mcp-list" -> Command.McpListServers
            "mcp-remove" -> parseMcpRemoveCommand(args, raw)
            "mcp-connect" -> parseMcpConnectCommand(args, raw)
            "mcp-disconnect" -> parseMcpDisconnectCommand(args, raw)
            "mcp-tools" -> parseMcpToolsCommand(args, raw)

            // Команды управления инвариантами агента
            "invariant" -> parseInvariantCommand(args, raw)

            // Команды управления событиями и уведомлениями (Wave 4 / Task3)
            "create-event" -> parseCreateEventCommand(args, raw)
            "notes" -> parseListNotesCommand(args)

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
    internal fun parseDropTaskCommand(args: String): Command {
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
    internal fun parseCloseTaskCommand(args: String): Command {
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
    internal fun parseCancelTaskCommand(args: String): Command {
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
     * Парсит команду планирования: `:plan` или `:plan <title> [description]`.
     *
     * - `:plan` (без аргументов) → FSM-команда `Command.Plan` для текущей открытой задачи
     * - `:plan <title> [description]` → legacy-команда `Command.PlanSteps`
     */
    internal fun parsePlanCommand(args: String): Command {
        if (args.isEmpty()) {
            // FSM-команда для текущей открытой задачи
            return Command.Plan
        }
        // Legacy-команда с явным указанием title
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
    internal fun parseProfileShowCommand(args: String): Command {
        return if (args.isEmpty()) {
            Command.ProfileShow(null)
        } else {
            Command.ProfileShow(args)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Парсинг команд управления debug-режимом
    // ═══════════════════════════════════════════════════════════════

    /**
     * Парсит команду управления debug-режимом: `:debug [on|off]`.
     *
     * - `:debug` (без аргументов) → переключить режим (TOGGLE)
     * - `:debug on` → включить debug-режим (ON)
     * - `:debug off` → выключить debug-режим (OFF)
     * - Любой другой аргумент → Unknown
     */
    internal fun parseDebugCommand(args: String, raw: String): Command {
        return when (args.lowercase()) {
            "" -> Command.Debug(DebugAction.TOGGLE)
            "on" -> Command.Debug(DebugAction.ON)
            "off" -> Command.Debug(DebugAction.OFF)
            else -> Command.Unknown(raw)
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

    /**
     * Парсит команды управления инвариантами: `:invariant add <rule>`, `:invariant list`, `:invariant remove <id>`.
     */
    internal fun parseInvariantCommand(args: String, raw: String): Command {
        val parts = args.split(" ", limit = 2)
        val subCommand = parts[0].lowercase()
        val subArgs = parts.getOrElse(1) { "" }.trim()

        return when (subCommand) {
            "add" -> {
                if (subArgs.isEmpty()) Command.Unknown(raw)
                else Command.InvariantAdd(subArgs)
            }

            "list" -> Command.InvariantList
            "remove" -> {
                val id = subArgs.toIntOrNull()
                if (id != null && id > 0) Command.InvariantRemove(id)
                else Command.Unknown(raw)
            }

            else -> Command.Unknown(raw)
        }
    }

    /**
     * Парсит команду :goto [state].
     * Без аргументов — Goto (показать карту состояний).
     * С аргументом — GotoState (явный переход).
     */
    internal fun parseGotoCommand(args: String): Command {
        return if (args.isEmpty()) {
            Command.Goto
        } else {
            Command.GotoState(args.uppercase())
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Парсинг команд управления MCP-серверами
    // ═══════════════════════════════════════════════════════════════

    /**
     * Парсит команду добавления MCP-сервера.
     * - `:mcp-add` → McpAddServerRequest (запуск интерактивного потока)
     * - `:mcp-add stdio <name> <command> [args...]` → McpAddServer с Stdio
     * - `:mcp-add http <name> <url>` → McpAddServer с StreamableHttp
     */
    internal fun parseMcpAddCommand(args: String, raw: String): Command {
        if (args.isEmpty()) return Command.McpAddServerRequest

        val parts = args.split(" ", limit = 2)
        val transportType = parts[0].lowercase()
        val rest = parts.getOrElse(1) { "" }.trim()

        if (rest.isEmpty()) return Command.Unknown(raw)

        return when (transportType) {
            "stdio" -> {
                val stdioParts = rest.split(" ", limit = 2)
                if (stdioParts.size < 2) return Command.Unknown(raw)
                val name = stdioParts[0]
                val cmdAndArgs = stdioParts[1].split(" ")
                val command = cmdAndArgs[0]
                val argsList = cmdAndArgs.drop(1)
                Command.McpAddServer(name, MCPTransport.Stdio(command, argsList))
            }

            "http" -> {
                val httpParts = rest.split(" ", limit = 2)
                if (httpParts.size < 2) return Command.Unknown(raw)
                val name = httpParts[0]
                val url = httpParts[1]
                Command.McpAddServer(name, MCPTransport.StreamableHttp(url))
            }

            else -> Command.Unknown(raw)
        }
    }

    /**
     * Парсит команду удаления MCP-сервера: `:mcp-remove <name>`.
     * Имя обязательно.
     */
    internal fun parseMcpRemoveCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.McpRemoveServerRequest
        } else {
            Command.McpRemoveServer(args)
        }
    }

    /**
     * Парсит команду подключения к MCP-серверу: `:mcp-connect <name>`.
     * Имя обязательно.
     */
    internal fun parseMcpConnectCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.McpConnectServer(args)
        }
    }

    /**
     * Парсит команду отключения от MCP-сервера: `:mcp-disconnect <name>`.
     * Имя обязательно.
     */
    internal fun parseMcpDisconnectCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.McpDisconnectServer(args)
        }
    }

    /**
     * Парсит команду получения инструментов MCP-сервера: `:mcp-tools <name>`.
     * Имя обязательно.
     */
    internal fun parseMcpToolsCommand(args: String, raw: String): Command {
        return if (args.isEmpty()) {
            Command.Unknown(raw)
        } else {
            Command.McpToolsServer(args)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Парсинг команд управления событиями и уведомлениями (Wave 4 / Task3)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Парсит команду создания события: `:create-event <date>`.
     * Дата в формате YYYY-MM-DD обязательна.
     */
    internal fun parseCreateEventCommand(args: String, raw: String): Command {
        if (args.isEmpty()) return Command.Unknown(raw)
        return try {
            val date = java.time.LocalDate.parse(args)
            Command.CreateEvent(date)
        } catch (e: Exception) {
            Command.Unknown(raw)
        }
    }

    /**
     * Парсит команду просмотра уведомлений: `:notes [limit]`.
     * limit опционален (дефолт 20).
     */
    internal fun parseListNotesCommand(args: String): Command {
        val limit = if (args.isEmpty()) null else args.toIntOrNull()
        return Command.ListNotes(limit)
    }

}


