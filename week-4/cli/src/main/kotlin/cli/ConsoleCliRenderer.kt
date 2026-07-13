package io.averkhogliad.ai.challenge.week4.cli.cli

import io.averkhogliad.ai.challenge.week4.cli.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.FsmRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.InvariantRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ProfileRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.TaskRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryStatus

/**
 * Консольная реализация [CliRenderer] — вывод в System.out.
 *
 * Минималистичная реализация без зависимостей от Mordant/Terminal.
 * При необходимости может быть заменена на Mordant-based реализацию.
 *
 * Делегирует рендеринг специализированным рендерерам:
 * - [ProfileRenderer] — профили
 * - [TaskRenderer] — задачи и шаги
 * - [InvariantRenderer] — инварианты
 * - [FsmRenderer] — FSM состояния, переходы, goto, debug
 */
class ConsoleCliRenderer : CliRenderer {

    // ──── Специализированные рендереры ────

    private val profileRenderer = ProfileRenderer()
    private val taskRenderer = TaskRenderer()
    private val invariantRenderer = InvariantRenderer()
    private val fsmRenderer = FsmRenderer()

    // ANSI color codes
    private val RESET = "\u001B[0m"
    private val GREEN = "\u001B[32m"
    private val RED = "\u001B[31m"
    private val YELLOW = "\u001B[33m"
    private val CYAN = "\u001B[36m"

    // ──── Прелоадер (анимированный спиннер) ────

    @Volatile
    private var spinnerRunning = false
    private var spinnerThread: Thread? = null
    private var spinnerMessage: String = ""

    override fun renderLoadingStart(message: String) {
        if (spinnerRunning) return
        spinnerMessage = message
        spinnerRunning = true
        spinnerThread = Thread {
            val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
            var frameIndex = 0
            try {
                while (spinnerRunning) {
                    val frame = frames[frameIndex % frames.size]
                    print("\r  $frame $spinnerMessage")
                    System.out.flush()
                    frameIndex++
                    Thread.sleep(80)
                }
            } catch (_: InterruptedException) {
                // spinner stopped
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    override fun renderLoadingStop() {
        if (!spinnerRunning) return
        spinnerRunning = false
        spinnerThread?.interrupt()
        spinnerThread = null
        print("\r" + " ".repeat(spinnerMessage.length + 4) + "\r")
        System.out.flush()
    }

    override fun renderMenu(executors: List<TaskExecutor>) {
        println()
        println("${CYAN}${"=".repeat(60)}${RESET}")
        println("${CYAN}  🎯 AI Challenge — Выбор задачи${RESET}")
        println("${CYAN}${"=".repeat(60)}${RESET}")
        for (executor in executors.sortedBy { it.taskId.value }) {
            val meta = executor.metadata
            println("  ${CYAN}${meta.id.value}.${RESET} ${meta.title}")
        }
        println("${CYAN}${"=".repeat(60)}${RESET}")
    }

    override fun renderTaskHeader(metadata: TaskMetadata) {
        println()
        println("${CYAN}${"-".repeat(60)}${RESET}")
        println("${CYAN}  📋 ${metadata.title}${RESET}")
        println("${CYAN}  💡 Команды: :back, :quit, :help, :params${RESET}")
        println("${CYAN}${"-".repeat(60)}${RESET}")
    }

    override fun renderResult(result: TaskResult) {
        when (result) {
            is TaskResult.Success -> {
                println()
                println(result.content)
                println()
                System.out.flush()
            }

            is TaskResult.Error -> renderError(result.message)
            is TaskResult.Partial -> {
                println()
                println(result.content)
                println()
                System.out.flush()
            }
        }
    }

    override fun renderError(message: String) {
        println()
        println("${RED}❌ [ОШИБКА] $message${RESET}")
        println()
    }

    override fun renderPrompt(state: CliState) {
        if (state.currentTaskId == null && !state.taskListMode) {
            print("${CYAN}🎯 Выберите задачу (номер, 0=выход, :help=помощь): ${RESET}")
        } else if (state.taskListMode) {
            print("${CYAN}📋 task> ${RESET}")
        } else {
            print("${CYAN}💬 prompt> ${RESET}")
        }
    }

    override fun renderHelp(state: CliState) {
        println()
        if (state.currentTaskId == null && !state.taskListMode) {
            println("${CYAN}📖 Доступные команды:${RESET}")
            println("  ${CYAN}:help, :h${RESET}            — эта справка")
            println("  ${CYAN}:quit, :q${RESET}            — выход из программы")
            println("  ${CYAN}:notes [limit]${RESET}       — показать уведомления (по умолчанию: 20)")
            println()

            println("${CYAN}🔌 MCP-серверы:${RESET}")
            println("  ${CYAN}:mcp-add${RESET}              — интерактивно добавить MCP-сервер")
            println("  ${CYAN}:mcp-add http <name> <url>${RESET}")
            println("  ${CYAN}:mcp-add stdio <name> <command> [args...]${RESET}")
            println("  ${CYAN}:mcp-list${RESET}             — список серверов")
            println("  ${CYAN}:mcp-remove <name>${RESET}    — удалить сервер")
            println("  ${CYAN}:mcp-connect <name>${RESET}   — подключиться")
            println("  ${CYAN}:mcp-disconnect <name>${RESET} — отключиться")
            println("  ${CYAN}:mcp-tools <name>${RESET}     — показать инструменты сервера")
            println()

            println("  ${CYAN}<номер>${RESET}               — выбрать задачу по номеру")
            println("  ${CYAN}0${RESET}                     — выход из программы")
        } else {
            println("${CYAN}📖 Доступные команды:${RESET}")
            println("  ${CYAN}:help, :h${RESET}     — эта справка")
            println("  ${CYAN}:quit, :q${RESET}     — выход из программы")
            println("  ${CYAN}:back, :b${RESET}     — вернуться к выбору задачи")
            println()

            println("${CYAN}📋 Команды управления задачами:${RESET}")
            println("  ${CYAN}:add <title>${RESET}              — добавить новую задачу")
            println("  ${CYAN}:tasks${RESET}                    — показать список задач")
            println("  ${CYAN}:edit [id] <title>${RESET}        — редактировать задачу (без id — текущую)")
            println("  ${CYAN}:drop [id]${RESET}                — удалить задачу (без id — текущую)")
            println("  ${CYAN}:open <id>${RESET}                — открыть задачу")
            println("  ${CYAN}:close [id]${RESET}               — закрыть задачу (без id — текущую)")
            println("  ${CYAN}:cancel [id]${RESET}              — отменить задачу (без id — текущую)")
            println()

            println("${CYAN}👣 Команды управления шагами:${RESET}")
            println("  ${CYAN}:step-add <text>${RESET}          — добавить шаг к текущей задаче")
            println("  ${CYAN}:step-list${RESET}                — показать список шагов")
            println("  ${CYAN}:step-done <id>${RESET}           — отметить шаг выполненным")
            println()

            println("${CYAN}🎯 Команды планирования и FSM:${RESET}")
            println("  ${CYAN}:plan [title] [desc]${RESET}      — сгенерировать план шагов через LLM")
            println("  ${CYAN}:debug [on|off]${RESET}           — вкл/выкл debug-режим (без аргументов — toggle)")
            println("  ${CYAN}:state${RESET}                    — показать состояние активной FSM-команды")
            println("  ${CYAN}:abort${RESET}                    — прервать активную FSM-команду")
            println()

            println("${CYAN}🧠 Команды памяти:${RESET}")
            println("  ${CYAN}:status${RESET}                   — показать состояние памяти (STM/WM/LTM)")
            println("  ${CYAN}:clear${RESET}                    — очистить STM текущей сессии")
            println("  ${CYAN}:ctx-save <content>${RESET}       — сохранить факт в LTM")
            println("  ${CYAN}:ctx-list${RESET}                 — показать все факты LTM")
            println("  ${CYAN}:ctx-forget <id>${RESET}          — удалить факт из LTM")
            println("  ${CYAN}:ctx-search <query>${RESET}       — поиск фактов в LTM")
            println()

            println("${CYAN}📅 События и уведомления:${RESET}")
            println("  ${CYAN}:create-event <date>${RESET}     — привязать текущую задачу к дате (YYYY-MM-DD)")
            println("  ${CYAN}:notes [limit]${RESET}           — показать уведомления (по умолчанию: 20)")
            println()

            println("${CYAN}🗂️  Команды индексации документов:${RESET}")
            println("  ${CYAN}:index <strategy> <path>${RESET}  — индексировать документы (fixed|structural)")
            println("  ${CYAN}:index-runs${RESET}               — история индексаций")
            println("  ${CYAN}:index-switch <runId>${RESET}     — переключить активный индекс")
            println("  ${CYAN}:index-stats [runId|all]${RESET}  — статистика индекса")
            println("  ${CYAN}:index-compare <id1> <id2>${RESET} — сравнить два индекса")
            println("  ${CYAN}:index-delete <runId>${RESET}     — удалить индекс (--before DATE, --keep-last N)")
            println("  ${CYAN}:index-clear [--all]${RESET}      — очистить неактивные индексы")
            println()

            println("${CYAN}🔍 RAG команды:${RESET}")
            println("  ${CYAN}:rag${RESET}                       — вкл/выкл RAG (toggle)")
            println("  ${CYAN}:rag status${RESET}                — состояние RAG")
            println("  ${CYAN}:rag list${RESET}                  — список доступных индексов")
            println("  ${CYAN}:rag mode <mode>${RESET}           — режим поиска (raw|filtered|reranked|rewrite)")
            println("  ${CYAN}:rag threshold <0..1>${RESET}      — порог фильтрации")
            println("  ${CYAN}:rag topk <initial> <final>${RESET} — настроить top-K")
            println("  ${CYAN}:rag config${RESET}                — текущая конфигурация RAG")
            println("  ${CYAN}:rag history [N]${RESET}           — история запросов (--detail <id>, --clear)")
            println("  ${CYAN}:rag analyze${RESET}               — анализ метрик (--compare <m1> <m2>)")
            println("  ${CYAN}:rag relevance <0..1>${RESET}      — порог релевантности (анти-галлюцинации)")
            println("  ${CYAN}:rag reset${RESET}                 — сбросить настройки RAG к конфигурации")
            println()

            println("${CYAN}💬 Команды чата (Task 5):${RESET}")
            println("  ${CYAN}:chat-new${RESET}                  — создать новый чат (текущий архивируется)")
            println("  ${CYAN}:chat-list${RESET}                 — список всех чатов")
            println("  ${CYAN}:chat-switch <id>${RESET}          — переключиться на чат")
            println("  ${CYAN}:chat-rename <name>${RESET}        — переименовать текущий чат")
            println("  ${CYAN}:chat-delete <id>${RESET}          — удалить чат")
            println("  ${CYAN}:chat-archive${RESET}              — архивировать текущий чат")
            println("  ${CYAN}:chat-history [N]${RESET}          — показать последние N сообщений")
            println()

            println("${CYAN}📝 Команды памяти задачи:${RESET}")
            println("  ${CYAN}:task-state${RESET}                — показать память задачи")
            println("  ${CYAN}:task-reset${RESET}                — сбросить память задачи")
            println("  ${CYAN}:task-goal <text>${RESET}          — установить цель диалога")
            println("  ${CYAN}:task-term add <name> <def>${RESET} — добавить термин")
            println("  ${CYAN}:task-term remove <name>${RESET}   — удалить термин")
            println("  ${CYAN}:task-constraint add <text>${RESET} — добавить ограничение")
            println("  ${CYAN}:task-constraint remove <idx>${RESET} — удалить ограничение")
            println()

            println("${CYAN}🔌 MCP-серверы:${RESET}")
            println("  ${CYAN}:mcp-add${RESET}                  — интерактивно добавить MCP-сервер")
            println("  ${CYAN}:mcp-add http <name> <url>${RESET}")
            println("  ${CYAN}:mcp-add stdio <name> <command> [args...]${RESET}")
            println("  ${CYAN}:mcp-list${RESET}                 — список серверов")
            println("  ${CYAN}:mcp-remove <name>${RESET}        — удалить сервер по имени")
            println("  ${CYAN}:mcp-connect <name>${RESET}       — подключиться к серверу по имени")
            println("  ${CYAN}:mcp-disconnect <name>${RESET}    — отключиться от сервера по имени")
            println("  ${CYAN}:mcp-tools <name>${RESET}         — показать инструменты сервера")
            println()

            println("${CYAN}⚙️  Команды управления параметрами LLM:${RESET}")
            println("  ${CYAN}:temp [value]${RESET}             — установить temperature (0.0-2.0); без аргументов — показать")
            println("  ${CYAN}:maxtokens [n]${RESET}            — установить max tokens; без аргументов — показать")
            println("  ${CYAN}:stop [s1,s2,...]${RESET}         — установить stop-последовательности; без аргументов — сбросить")
            println("  ${CYAN}:reset${RESET}                    — сбросить все параметры к значениям по умолчанию")
            println("  ${CYAN}:params${RESET}                   — показать текущие параметры")
            println()

            println("${CYAN}👤 Команды управления профилями:${RESET}")
            println("  ${CYAN}:profile-new <name>${RESET}       — создать новый профиль")
            println("  ${CYAN}:profile-list${RESET}             — список всех профилей")
            println("  ${CYAN}:profile-use <name>${RESET}       — активировать профиль по имени (:profile-use none — деактивировать)")
            println("  ${CYAN}:profile-edit <name>${RESET}      — редактировать профиль")
            println("  ${CYAN}:profile-delete <name>${RESET}    — удалить профиль")
            println("  ${CYAN}:profile-show [name]${RESET}      — показать содержимое профиля")
            println()

            println("${CYAN}🛡️  Команды управления инвариантами:${RESET}")
            println("  ${CYAN}:invariant add <rule>${RESET}     — добавить новый инвариант")
            println("  ${CYAN}:invariant list${RESET}           — показать список инвариантов")
            println("  ${CYAN}:invariant remove <id>${RESET}    — удалить инвариант по ID")
        }
        println()
    }

    override fun renderParameters(state: CliState) {
        val config = state.executionConfig
        println()
        println("${CYAN}⚙️  Текущие параметры:${RESET}")
        println("  ${CYAN}temperature:${RESET} ${config.temperature}")
        println("  ${CYAN}maxTokens:${RESET}   ${config.maxTokens}")
        println(
            "  ${CYAN}stopSequences:${RESET} ${
                if (config.stopSequences.isEmpty()) "нет" else config.stopSequences.joinToString(
                    ", "
                )
            }"
        )
        println("  ${CYAN}modelId:${RESET}     ${config.modelId ?: "default"}")
        println()
    }

    override fun renderWelcome() {
        println()
        println("${CYAN}👋 Добро пожаловать в AI Challenge CLI week-4!${RESET}")
        println("${CYAN}💡 Введите :help, чтобы посмотреть доступные команды.${RESET}")
        println()
    }

    override fun renderGoodbye() {
        println()
        println("${CYAN}👋 До свидания!${RESET}")
        println()
    }

    override fun renderRequestInfo(prompt: String, config: TaskExecutionConfig) {
        println()
        println("${CYAN}═══ 📤 Отправка запроса ═══${RESET}")
        println("${CYAN}Промпт:${RESET} $prompt")
        println("${CYAN}Параметры:${RESET} температура=${config.temperature}, maxTokens=${config.maxTokens}")
        if (config.modelId != null) {
            println("${CYAN}Модель:${RESET} ${config.modelId.value}")
        }
        println("${CYAN}═══════════════════════${RESET}")
        println()
    }

    override fun renderInfo(message: String) {
        println()
        println("${CYAN}ℹ️  [INFO] $message${RESET}")
        println()
    }

    override fun renderSuccess(message: String) {
        println()
        println("${GREEN}✅ [УСПЕХ] $message${RESET}")
        println()
    }

    // ──── Делегирование: Todo-manager ────

    override fun renderTaskList(tasks: List<Task>) = taskRenderer.renderTaskList(tasks)
    override fun renderTaskDetail(task: Task) = taskRenderer.renderTaskDetail(task)
    override fun renderTaskCreated(taskId: TaskId) = taskRenderer.renderTaskCreated(taskId)
    override fun renderTaskUpdated(taskId: TaskId) = taskRenderer.renderTaskUpdated(taskId)
    override fun renderTaskDeleted(taskId: TaskId) = taskRenderer.renderTaskDeleted(taskId)
    override fun renderTaskClosed(taskId: TaskId) = taskRenderer.renderTaskClosed(taskId)
    override fun renderTaskCancelled(taskId: TaskId) = taskRenderer.renderTaskCancelled(taskId)

    // ──── Делегирование: Step management ────

    override fun renderStepCreated(step: TaskStep) = taskRenderer.renderStepCreated(step)
    override fun renderStepList(steps: List<TaskStep>) = taskRenderer.renderStepList(steps)
    override fun renderStepCompleted(step: TaskStep) = taskRenderer.renderStepCompleted(step)
    override fun renderStepError(message: String) = taskRenderer.renderStepError(message)

    // ──── Memory management ────

    override fun renderMemoryStatus(status: MemoryStatus) {
        println()
        println("${CYAN}=== 🧠 Состояние памяти ===${RESET}")
        println(
            "${CYAN}Уровень:${RESET} ${
                when (status.level) {
                    SessionLevel.TASK_LIST -> "Список задач"
                    SessionLevel.TASK_DETAIL -> "Задача ${status.taskId?.value ?: "неизвестно"}"
                }
            }"
        )
        println()
        println("${CYAN}STM (краткосрочная память):${RESET}")
        println("  Сообщений: ${status.messageCount}")
        println("  Создано: ${status.createdAt}")
        println("  Обновлено: ${status.updatedAt}")
        println()
        println("${CYAN}LTM (долговременная память):${RESET}")
        println("  Фактов: ${status.ltmFactCount}")
        println()
    }

    override fun renderMemoryCleared() {
        println()
        println("${GREEN}✅ STM очищена${RESET}")
        println()
    }

    // ──── LTM (Long-Term Memory) rendering ────

    override fun renderFactSaved(fact: Fact) {
        println()
        println("${GREEN}✅ Факт сохранён: [${fact.id.value}] ${fact.content}${RESET}")
        println()
    }

    override fun renderFactList(facts: List<Fact>) {
        println()
        println("${CYAN}=== 📚 База знаний (LTM) ===${RESET}")
        println("${CYAN}Всего фактов:${RESET} ${facts.size}")
        println()
        if (facts.isEmpty()) {
            println("${YELLOW}Нет сохранённых фактов${RESET}")
        } else {
            facts.forEachIndexed { index, fact ->
                println("${index + 1}. [${fact.id.value}] ${fact.content}")
                println("   Создан: ${fact.createdAt}")
            }
        }
        println()
    }

    override fun renderFactForgotten(factId: String) {
        println()
        println("${GREEN}✅ Факт удалён: [$factId]${RESET}")
        println()
    }

    override fun renderFactNotFound(factId: String) {
        println()
        println("${RED}❌ [ОШИБКА] Факт не найден: [$factId]${RESET}")
        println()
    }

    override fun renderFactSearchResults(facts: List<Fact>, query: String) {
        println()
        println("${CYAN}=== 🔍 Результаты поиска: \"$query\" ===${RESET}")
        println("${CYAN}Найдено фактов:${RESET} ${facts.size}")
        println()
        if (facts.isEmpty()) {
            println("${YELLOW}Ничего не найдено${RESET}")
        } else {
            facts.forEach { fact ->
                println("  [${fact.id.value}] ${fact.content}")
                println("  Создан: ${fact.createdAt}")
                println()
            }
        }
    }

    override fun renderFactSearchEmpty(query: String) {
        println()
        println("${CYAN}=== 🔍 Результаты поиска: \"$query\" ===${RESET}")
        println("${YELLOW}Ничего не найдено${RESET}")
        println()
    }

    // ──── Делегирование: Profile rendering ────

    override fun renderProfileList(profiles: List<Profile>) = profileRenderer.renderProfileList(profiles)
    override fun renderProfileDetail(profile: Profile) = profileRenderer.renderProfileDetail(profile)
    override fun renderProfileDeleted(name: String) = profileRenderer.renderProfileDeleted(name)
    override fun renderProfileUpdated(name: String) = profileRenderer.renderProfileUpdated(name)
    override fun renderProfileError(message: String) = profileRenderer.renderProfileError(message)
    override fun renderMultilineInputPrompt() = profileRenderer.renderMultilineInputPrompt()
    override fun renderProfileDescriptionPrompt() = profileRenderer.renderProfileDescriptionPrompt()
    override fun renderProfileInstructionsPrompt() = profileRenderer.renderProfileInstructionsPrompt()
    override fun renderProfileNotFoundById(id: String) = profileRenderer.renderProfileNotFoundById(id)
    override fun renderProfileNotFoundByName(name: String) = profileRenderer.renderProfileNotFoundByName(name)
    override fun renderProfileAlreadyExists(name: String) = profileRenderer.renderProfileAlreadyExists(name)
    override fun renderMissingProfileId() = profileRenderer.renderMissingProfileId()
    override fun renderMissingProfileName() = profileRenderer.renderMissingProfileName()
    override fun renderEmptyProfileContent() = profileRenderer.renderEmptyProfileContent()
    override fun renderCannotDeleteActiveProfile() = profileRenderer.renderCannotDeleteActiveProfile()
    override fun renderProfileContentTooLong(length: Int) = profileRenderer.renderProfileContentTooLong(length)
    override fun renderStatusProfile(profileName: String?) = profileRenderer.renderStatusProfile(profileName)

    // ──── Делегирование: FSM rendering ────

    override fun renderFsmState(state: CommandState) = fsmRenderer.renderFsmState(state)
    override fun renderFsmStateInfo(state: CommandState) = fsmRenderer.renderFsmStateInfo(state)
    override fun renderNoActiveCommand() = fsmRenderer.renderNoActiveCommand()
    override fun renderAbortConfirmation() = fsmRenderer.renderAbortConfirmation()
    override fun renderAbortSuccess() = fsmRenderer.renderAbortSuccess()
    override fun renderAbortCancelled() = fsmRenderer.renderAbortCancelled()
    override fun renderStatusFsm(stage: CommandStage?, availableTransitions: List<Transition>) =
        fsmRenderer.renderStatusFsm(stage, availableTransitions)

    override fun renderStateMap(stateMap: StateMap) = fsmRenderer.renderStateMap(stateMap)
    override fun renderGotoSuccess(from: CommandStage, to: CommandStage) = fsmRenderer.renderGotoSuccess(from, to)
    override fun renderGotoError(reason: String) = fsmRenderer.renderGotoError(reason)
    override fun renderGotoNoActiveCommand() = fsmRenderer.renderGotoNoActiveCommand()
    override fun renderGotoInvalidState(stateName: String) = fsmRenderer.renderGotoInvalidState(stateName)
    override fun renderAvailableTransitions(transitions: List<Transition>) =
        fsmRenderer.renderAvailableTransitions(transitions)

    override fun renderStatusDebug(enabled: Boolean) = fsmRenderer.renderStatusDebug(enabled)
    override fun renderStatusActiveCommand(commandName: String?) = fsmRenderer.renderStatusActiveCommand(commandName)

    // ──── Делегирование: Invariant rendering ────

    override fun renderInvariantList(invariants: List<Invariant>) = invariantRenderer.renderInvariantList(invariants)
    override fun renderInvariantAdded(invariant: Invariant) = invariantRenderer.renderInvariantAdded(invariant)
    override fun renderInvariantRemoved(id: Int) = invariantRenderer.renderInvariantRemoved(id)
    override fun renderInvariantNotFound(id: Int) = invariantRenderer.renderInvariantNotFound(id)
    override fun renderInvariantEmptyRule() = invariantRenderer.renderInvariantEmptyRule()
    override fun renderInvariantRemoveConfirmation(id: Int) = invariantRenderer.renderInvariantRemoveConfirmation(id)
    override fun renderStatusInvariants(count: Int) = invariantRenderer.renderStatusInvariants(count)

    // ──── Debug mode pause ────

    override fun waitForEnter() {
        print("${CYAN}⏎ Нажмите Enter для продолжения...${RESET}")
        System.out.flush()
        try {
            readlnOrNull()
        } catch (_: Exception) {
            // ignore exceptions (e.g., EOF in non-interactive environments)
        }
    }

    // ──── LLM telemetry ────

    override fun renderTelemetry(result: TaskResult) {
        val tokenUsage = when (result) {
            is TaskResult.Success -> result.tokenUsage
            is TaskResult.Partial -> result.tokenUsage
            is TaskResult.Error -> result.tokenUsage
        } ?: return

        val sep = "${CYAN}${"-".repeat(70)}${RESET}"
        println()
        println(sep)
        println("${CYAN}📊 Телеметрия:${RESET}")
        println("   ${CYAN}Prompt токенов:${RESET}       ${tokenUsage.promptTokens}")
        println("   ${CYAN}Completion токенов:${RESET}   ${tokenUsage.completionTokens}")
        println("   ${CYAN}Всего токенов:${RESET}        ${tokenUsage.totalTokens}")
        println(sep)
        System.out.flush()
    }
}
