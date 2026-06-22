package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.model.*

/**
 * Консольная реализация [CliRenderer] — вывод в System.out.
 *
 * Минималистичная реализация без зависимостей от Mordant/Terminal.
 * При необходимости может быть заменена на Mordant-based реализацию.
 */
class ConsoleCliRenderer : CliRenderer {

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
        // Очищаем строку спиннера
        print("\r" + " ".repeat(spinnerMessage.length + 4) + "\r")
        System.out.flush()
    }

    override fun renderMenu(executors: List<TaskExecutor>) {
        println()
        println("=".repeat(60))
        println("  AI Challenge — Выбор задачи")
        println("=".repeat(60))
        for (executor in executors.sortedBy { it.taskId.value }) {
            val meta = executor.metadata
            println("  ${meta.id.value}. ${meta.title}")
            println("     ${meta.description}")
        }
        println("=".repeat(60))
    }

    override fun renderTaskHeader(metadata: TaskMetadata) {
        println()
        println("-".repeat(60))
        println("  ${metadata.title}")
        println("  ${metadata.description}")
        if (metadata.availableCommands.isNotEmpty()) {
            println("  Доступные команды: ${metadata.availableCommands.joinToString(", ")}")
        }
        println("-".repeat(60))
    }

    override fun renderResult(result: TaskResult) {
        when (result) {
            is TaskResult.Success -> {
                println()
                println(result.content)
                println()
                System.out.flush()
            }

            is TaskResult.Error -> {
                renderError(result.message)
            }

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
        println("[ОШИБКА] $message")
        println()
    }

    override fun renderPrompt(state: CliState) {
        if (state.currentTaskId == null) {
            print("Выберите задачу (номер, 0=выход, :help=помощь): ")
        } else {
            print("prompt> ")
        }
    }

    override fun renderHelp(state: CliState) {
        println()
        if (state.currentTaskId == null) {
            println("Доступные команды:")
            println("  :help, :h     — эта справка")
            println("  :quit, :q     — выход из программы")
            println("  <номер>       — выбрать задачу по номеру")
            println("  0             — выход из программы")
        } else {
            println("Доступные команды:")
            println("  :help, :h     — эта справка")
            println("  :quit, :q     — выход из программы")
            println("  :back, :b     — вернуться к выбору задачи")
            println("  :temp [value] — установить температуру (0.0-2.0); без аргументов — показать")
            println("  :maxtokens [n]— установить макс. кол-во токенов; без аргументов — показать")
            println("  :stop [s1,s2] — установить стоп-последовательности; без аргументов — сбросить")
            println("  :reset        — сбросить все параметры к значениям по умолчанию")
            println("  :params       — показать текущие параметры")
            println()
            println("Команды диалогов:")
            println("  :new [title]  — создать новый диалог (заголовок опционально)")
            println("  :list         — список всех диалогов")
            println("  :history [id] — показать историю сообщений диалога (по умолчанию — текущий)")
            println("  :switch <id>  — переключиться на диалог по ID")
            println("  :delete <id>  — удалить диалог по ID")

            // ──── Команды управления профилями ────
            println()
            println("Команды управления профилями:")
            println("  :profile-new <name>      — создать новый профиль")
            println("  :profile-list            — список всех профилей")
            println("  :profile-use <name>      — активировать профиль по имени (:profile-use none — деактивировать)")
            println("  :profile-edit <name>     — редактировать профиль")
            println("  :profile-delete <name>   — удалить профиль")
            println("  :profile-show [name]     — показать содержимое профиля")

            // ──── Task 4: команды сжатия контекста ────
            if ((state.currentTaskId ?: 0) >= 4) {
                println()
                println("Команды сжатия контекста:")
                println("  :compression on          — включить сжатие")
                println("  :compression off         — выключить сжатие")
                println("  :compression window <N>  — размер скользящего окна")
                println("  :compression block <K>   — размер блока для суммаризации")
                println("  :compression status      — показать текущий статус сжатия")
            }

            // ──── Task 5: команды стратегий контекста ────
            if (state.currentTaskId == 5) {
                println()
                println("Команды стратегий:")
                println("  :strategy [index]        — меню/выбор стратегии")
                println("  :strategy info           — показать текущую стратегию")
                println()
                println("Команды веток (Branching):")
                println("  :branch create <name>    — создать новую ветку")
                println("  :branch switch <name>    — переключиться на ветку")
                println("  :branch list             — список веток")
                println()
                println("Команды чекпоинтов (Branching):")
                println("  :checkpoint              — создать чекпоинт")
                println("  :checkpoint list         — список чекпоинтов")
                println()
                println("Команды фактов (Sticky Facts):")
                println("  :facts                   — список фактов")
                println("  :facts add <key>=<value> — добавить факт")
                println("  :facts remove <key>      — удалить факт")
                println("  :facts clear             — очистить все факты")
            }
        }
        println()
    }

    override fun renderParameters(state: CliState) {
        val config = state.executionConfig
        println()
        println("Текущие параметры:")
        println("  temperature: ${config.temperature}")
        println("  maxTokens:   ${config.maxTokens}")
        println("  stopSequences: ${if (config.stopSequences.isEmpty()) "нет" else config.stopSequences.joinToString(", ")}")
        println("  modelId:     ${config.modelId ?: "default"}")
        println()
    }

    override fun renderWelcome() {
        println()
        println("Добро пожаловать в AI Challenge!")
        println("Введите :help для справки.")
        println()
    }

    override fun renderGoodbye() {
        println()
        println("До свидания!")
    }

    override fun renderRequestInfo(
        prompt: String,
        config: io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
    ) {
        println()
        println("═══ Отправка запроса ═══")
        println("Промпт: $prompt")
        println("Параметры: температура=${config.temperature}, maxTokens=${config.maxTokens}")
        if (config.modelId != null) {
            println("Модель: ${config.modelId.value}")
        }
        println("═══════════════════════")
        println()
    }

    override fun renderInfo(message: String) {
        println()
        println("[INFO] $message")
        println()
    }

    override fun renderSuccess(message: String) {
        println()
        println("[SUCCESS] $message")
        println()
    }

    // ──── Todo-manager rendering methods ────

    override fun renderTaskList(tasks: List<Task>) {
        println()
        if (tasks.isEmpty()) {
            println("No tasks found")
        } else {
            println("Tasks:")
            tasks.forEach { task ->
                val statusIcon = when (task.status) {
                    TaskStatus.OPEN -> "○"
                    TaskStatus.CLOSED -> "✓"
                    TaskStatus.CANCELLED -> "✗"
                }
                println("  $statusIcon [${task.id.value}] ${task.title}")
            }
        }
        println()
    }

    override fun renderTaskDetail(task: Task) {
        println()
        println("Task: ${task.title}")
        println("  ID: ${task.id.value}")
        println("  Status: ${task.status}")
        println("  Created: ${task.createdAt}")
        println("  Updated: ${task.updatedAt}")
        println()
        println("  Description:")
        if (task.hasDescription()) {
            println("    ${task.description}")
        } else {
            println("    (Описание отсутствует. Используйте :describe ${task.id.value} для добавления)")
        }
        println()
    }

    override fun renderTaskCreated(taskId: TaskId) {
        println()
        println("✓ Task created: ${taskId.value}")
        println()
    }

    override fun renderTaskUpdated(taskId: TaskId) {
        println()
        println("✓ Task updated: ${taskId.value}")
        println()
    }

    override fun renderTaskDeleted(taskId: TaskId) {
        println()
        println("✓ Task deleted: ${taskId.value}")
        println()
    }

    override fun renderTaskClosed(taskId: TaskId) {
        println()
        println("✓ Task closed: ${taskId.value}")
        println()
    }

    override fun renderTaskCancelled(taskId: TaskId) {
        println()
        println("✓ Task cancelled: ${taskId.value}")
        println()
    }

    // ──── Step management rendering methods ────

    override fun renderStepCreated(step: TaskStep) {
        println()
        println("\u2713 Step created: [${step.id.value}] ${if (step.isCompleted) "[x]" else "[ ]"} ${step.text}")
        println()
    }

    override fun renderStepList(steps: List<TaskStep>) {
        println()
        if (steps.isEmpty()) {
            println("No steps found for this task")
        } else {
            println("Steps:")
            steps.sortedBy { it.order }.forEach { step ->
                val marker = if (step.isCompleted) "[x]" else "[ ]"
                println("  $marker [${step.id.value}] ${step.text}")
            }
        }
        println()
    }

    override fun renderStepCompleted(step: TaskStep) {
        println()
        println("\u2713 Step completed: [${step.id.value}] ${step.text}")
        println()
    }

    override fun renderStepError(message: String) {
        println()
        println("[STEP ERROR] $message")
        println()
    }

    // ──── Memory management rendering methods ────

    override fun renderMemoryStatus(status: io.averkhogliad.ai.challenge.week2.domain.service.MemoryStatus) {
        println()
        println("=== Состояние памяти ===")
        println(
            "Уровень: ${
                when (status.level) {
                    io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_LIST -> "Список задач"
                    io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_DETAIL -> "Задача ${status.taskId?.value ?: "неизвестно"}"
                }
            }"
        )
        println()
        println("STM (краткосрочная память):")
        println("  Сообщений: ${status.messageCount}")
        println("  Создано: ${status.createdAt}")
        println("  Обновлено: ${status.updatedAt}")
        println()
        println("LTM (долговременная память):")
        println("  Фактов: ${status.ltmFactCount}")
        println()
    }

    override fun renderMemoryCleared() {
        println()
        println("✓ STM очищена")
        println()
    }

    // ──── LTM (Long-Term Memory) rendering methods ────

    override fun renderFactSaved(fact: Fact) {
        println()
        println("✓ Факт сохранён: [${fact.id.value}] ${fact.content}")
        println()
    }

    override fun renderFactList(facts: List<Fact>) {
        println()
        println("=== База знаний (LTM) ===")
        println("Всего фактов: ${facts.size}")
        println()
        if (facts.isEmpty()) {
            println("Нет сохранённых фактов")
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
        println("✓ Факт удалён: [$factId]")
        println()
    }

    override fun renderFactNotFound(factId: String) {
        println()
        println("[ОШИБКА] Факт не найден: [$factId]")
        println()
    }

    override fun renderFactSearchResults(facts: List<Fact>, query: String) {
        println()
        println("=== Результаты поиска: \"$query\" ===")
        println("Найдено фактов: ${facts.size}")
        println()
        if (facts.isEmpty()) {
            println("Ничего не найдено")
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
        println("=== Результаты поиска: \"$query\" ===")
        println("Ничего не найдено")
        println()
    }

    // ──── Profile rendering methods ────

    /**
     * Отрендерить список профилей.
     *
     * @param profiles список профилей
     */
    override fun renderProfileList(profiles: List<io.averkhogliad.ai.challenge.week2.domain.model.Profile>) {
        println()
        if (profiles.isEmpty()) {
            println("Профили не найдены")
        } else {
            val activeProfile = profiles.find { it.isActive }
            println("Профили:")
            profiles.forEachIndexed { index, profile ->
                val marker = if (profile.isActive) "*" else " "
                println("  ${index + 1}. $marker ${profile.name} (id: ${profile.id.value.take(8)}...)")
            }
            if (activeProfile == null) {
                println("Активный профиль не задан")
            }
        }
        println()
    }

    /**
     * Отрендерить детальную информацию о профиле.
     *
     * @param profile профиль для отображения
     */
    override fun renderProfileDetail(profile: io.averkhogliad.ai.challenge.week2.domain.model.Profile) {
        println()
        println("=".repeat(60))
        println("  Профиль: ${profile.name}")
        println("=".repeat(60))
        println("  ID: ${profile.id.value}")
        println("  Статус: ${if (profile.isActive) "АКТИВЕН" else "неактивен"}")
        println("  Создан: ${profile.createdAt}")
        println("  Обновлён: ${profile.updatedAt}")
        println("-".repeat(60))
        println("  Описание:")
        println(if (profile.description.isNotEmpty()) profile.description else "(не задано)")
        println("-".repeat(60))
        println("  Инструкции:")
        println(if (profile.instructions.isNotEmpty()) profile.instructions else "(не задано)")
        println("-".repeat(60))
        println()
    }

    /**
     * Отрендерить сообщение об удалении профиля.
     *
     * @param name название удалённого профиля
     */
    override fun renderProfileDeleted(name: String) {
        println()
        println("✓ Профиль \"$name\" удалён")
        println()
    }

    /**
     * Отрендерить сообщение об обновлении профиля.
     *
     * @param name название обновлённого профиля
     */
    override fun renderProfileUpdated(name: String) {
        println()
        println("✓ Профиль \"$name\" обновлён")
        println()
    }

    /**
     * Отрендерить сообщение об ошибке операции с профилем.
     *
     * @param message сообщение об ошибке
     */
    override fun renderProfileError(message: String) {
        println()
        println("[ОШИБКА] $message")
        println()
    }

    /**
     * Отрендерить приглашение к многострочному вводу содержимого профиля.
     */
    override fun renderMultilineInputPrompt() {
        println("Введите содержимое профиля (завершите :done, отмените :cancel):")
        print("> ")
    }

    override fun renderProfileDescriptionPrompt() {
        println()
        println("Введите описание профиля (завершите :done, отмените :cancel):")
        print("> ")
    }

    override fun renderProfileInstructionsPrompt() {
        println()
        println("Введите инструкции профиля (завершите :done, отмените :cancel):")
        print("> ")
    }

    // ──── Profile error rendering methods ────

    /**
     * Отрендерить ошибку: профиль не найден по ID.
     *
     * @param id идентификатор профиля
     */
    override fun renderProfileNotFoundById(id: String) {
        println()
        println("Ошибка: Профиль с ID \"$id\" не найден")
        println()
    }

    /**
     * Отрендерить ошибку: профиль не найден по имени.
     *
     * @param name имя профиля
     */
    override fun renderProfileNotFoundByName(name: String) {
        println()
        println("Ошибка: Профиль с именем \"$name\" не найден")
        println()
    }

    /**
     * Отрендерить ошибку: профиль с таким названием уже существует.
     *
     * @param name имя профиля
     */
    override fun renderProfileAlreadyExists(name: String) {
        println()
        println("Ошибка: Профиль с названием \"$name\" уже существует")
        println()
    }

    /**
     * Отрендерить ошибку: не указан ID профиля для активации.
     */
    override fun renderMissingProfileId() {
        println()
        println("Ошибка: Укажите ID профиля. Использование: :profile-activate <id>")
        println()
    }

    /**
     * Отрендерить ошибку: не указано имя профиля для создания.
     */
    override fun renderMissingProfileName() {
        println()
        println("Ошибка: Укажите имя профиля. Использование: :profile-create <name>")
        println()
    }

    /**
     * Отрендерить ошибку: пустое содержимое профиля.
     */
    override fun renderEmptyProfileContent() {
        println()
        println("Ошибка: Содержимое профиля не может быть пустым")
        println()
    }

    /**
     * Отрендерить ошибку: попытка удалить активный профиль.
     */
    override fun renderCannotDeleteActiveProfile() {
        println()
        println("Ошибка: Нельзя удалить активный профиль. Сначала переключитесь на другой профиль.")
        println()
    }

    /**
     * Отрендерить ошибку: содержимое профиля превышает лимит.
     *
     * @param length текущая длина содержимого
     */
    override fun renderProfileContentTooLong(length: Int) {
        println()
        println("Ошибка: Содержимое профиля не может превышать 1000 символов (текущая длина: $length)")
        println()
    }

    // ──── Profile status rendering ────

    /**
     * Отрендерить информацию о профиле в выводе команды :status.
     *
     * @param profileName название активного профиля или null, если профиль не задан
     */
    override fun renderStatusProfile(profileName: String?) {
        println()
        if (profileName != null) {
            println("PM: Активный профиль \"$profileName\"")
        } else {
            println("PM: Профиль не задан")
        }
        println()
    }

    // ──── Debug mode status rendering ────

    /**
     * Отрендерить информацию о статусе debug-режима в выводе команды :status.
     *
     * @param enabled true если debug-режим включен, false если выключен
     */
    override fun renderStatusDebug(enabled: Boolean) {
        println()
        if (enabled) {
            println("Debug mode: enabled")
        } else {
            println("Debug mode: disabled")
        }
        println()
    }

    // ──── Active FSM command status rendering ────

    /**
     * Отрендерить информацию о активной FSM-команде в выводе команды :status.
     *
     * @param commandName имя активной команды или null, если нет активной команды
     */
    override fun renderStatusActiveCommand(commandName: String?) {
        println()
        if (commandName != null) {
            println("Active command: $commandName")
        } else {
            println("Active command: none")
        }
        println()
    }

    // ──── FSM (Finite State Machine) visualization ────

    /**
     * Отрендерить текущее состояние FSM для debug-режима.
     *
     * Выводит информацию о выполняемой команде:
     * - имя команды
     * - текущий этап (PLANNING, EXECUTION, VALIDATION, DONE)
     * - текущий шаг внутри этапа
     * - ожидаемое действие
     * - контекст выполнения (если есть)
     *
     * @param state текущее состояние FSM
     */
    override fun renderFsmState(state: io.averkhogliad.ai.challenge.week2.domain.model.CommandState) {
        println()
        println("[DEBUG] Command: ${state.commandName}")
        println("[DEBUG] Stage: ${state.currentStage}")
        println("[DEBUG] Step: ${state.currentStep}")
        if (state.expectedAction.isNotEmpty()) {
            println("[DEBUG] Action: ${state.expectedAction}")
        }
        if (state.context.isNotEmpty()) {
            println("[DEBUG] Context:")
            state.context.forEach { (key, value) ->
                println("  $key: $value")
            }
        }
        println()
    }

    // ──── Debug mode pause after step execution ────

    /**
     * Выводит подсказку "Press Enter to continue..." и блокирует поток
     * до нажатия клавиши Enter. Используется в debug-режиме для пошаговой отладки.
     */
    override fun waitForEnter() {
        print("Press Enter to continue...")
        System.out.flush()
        try {
            readlnOrNull()
        } catch (_: Exception) {
            // ignore exceptions (e.g., EOF in non-interactive environments)
        }
    }

    // ──── FSM state command rendering (:state) ────

    /**
     * Отрендерить информацию о текущем состоянии FSM для команды :state.
     *
     * Выводит информацию о выполняемой команде:
     * - имя команды
     * - текущий этап (PLANNING, EXECUTION, VALIDATION, DONE)
     * - текущий шаг внутри этапа
     * - ожидаемое действие
     * - контекст выполнения (если есть)
     *
     * @param state текущее состояние FSM
     */
    override fun renderFsmStateInfo(state: io.averkhogliad.ai.challenge.week2.domain.model.CommandState) {
        println()
        println("=== Состояние FSM ===")
        println("Команда: ${state.commandName}")
        println("Этап: ${state.currentStage}")
        println("Шаг: ${state.currentStep}")
        if (state.expectedAction.isNotEmpty()) {
            println("Ожидаемое действие: ${state.expectedAction}")
        }
        if (state.context.isNotEmpty()) {
            println("Контекст:")
            state.context.forEach { (key, value) ->
                println("  $key: $value")
            }
        }
        println()
    }

    /**
     * Отрендерить сообщение об отсутствии активной команды.
     */
    override fun renderNoActiveCommand() {
        println()
        println("Нет активной команды")
        println()
    }

    // ──── Abort command rendering (:abort) ────

    /**
     * Отрендерить запрос подтверждения прерывания команды.
     */
    override fun renderAbortConfirmation() {
        println()
        print("Прервать выполнение команды? (y/n): ")
        System.out.flush()
    }

    /**
     * Отрендерить сообщение об успешном прерывании команды.
     */
    override fun renderAbortSuccess() {
        println()
        println("✓ Команда прервана")
        println()
    }

    /**
     * Отрендерить сообщение об отмене прерывания команды.
     */
    override fun renderAbortCancelled() {
        println()
        println("Прерывание отменено")
        println()
    }
}
