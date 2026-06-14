package io.averkhogliad.ai.challenge.week1.cli

import io.averkhogliad.ai.challenge.week1.application.executor.DialogManagerAccessor
import io.averkhogliad.ai.challenge.week1.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week1.cli.commands.Command
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskId
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.model.FactCategory
import io.averkhogliad.ai.challenge.week1.domain.strategy.BranchingStrategy
import io.averkhogliad.ai.challenge.week1.domain.strategy.ContextStrategyManager
import io.averkhogliad.ai.challenge.week1.domain.strategy.StickyFactsStrategy

/**
 * Обработчик команд CLI — чистая функция `Command × CliState → CliState`.
 *
 * Не содержит бизнес-логики — только маршрутизация команд:
 * - Глобальные команды (Help, Back, Quit, SelectTask) → изменение [CliState]
 * - Команды параметров (SetTemperature, SetMaxTokens, etc.) → обновление [CliState.executionConfig]
 * - Команды диалогов (NewDialog, ListDialogs, etc.) → делегирование в [Task2Executor]
 * - [Command.UserInput] → делегирование в [TaskExecutor.execute]
 */
class CommandHandler(
    private val executors: Map<TaskId, TaskExecutor>,
    private val compressionConfigProvider: ContextCompressionConfigProvider? = null,
    private val contextStrategyManager: ContextStrategyManager? = null
) {

    suspend fun handle(command: Command, state: CliState): CliState {
        return when (command) {
            // Глобальные команды
            is Command.Help -> state
            is Command.Back -> state.copy(currentTaskId = null, currentDialogId = null)
            is Command.Quit -> state.copy(isRunning = false)
            is Command.SelectTask -> state.copy(currentTaskId = command.taskId)

            // LLM параметры
            is Command.SetTemperature -> state.copy(
                executionConfig = state.executionConfig.copy(temperature = command.value)
            )

            is Command.SetMaxTokens -> state.copy(
                executionConfig = state.executionConfig.copy(maxTokens = command.value)
            )

            is Command.SetStopSequences -> state.copy(
                executionConfig = state.executionConfig.copy(stopSequences = command.values)
            )

            is Command.ResetParameters -> state.copy(
                executionConfig = TaskExecutionConfig()
            )

            is Command.ShowParameters -> state

            // Команды управления диалогами (для Task 2)
            is Command.NewDialog -> handleNewDialog(command, state)
            is Command.ListDialogs -> state // Обработка в CliApplication
            is Command.DeleteDialog -> state // Обработка в CliApplication
            is Command.SwitchDialog -> handleSwitchDialog(command, state)

            // Команды управления сжатием контекста (для Task 4)
            is Command.SetCompressionEnabled -> {
                compressionConfigProvider?.setEnabled(command.enabled)
                state
            }

            is Command.SetCompressionWindow -> {
                compressionConfigProvider?.setWindowSize(command.size)
                state
            }

            is Command.SetCompressionBlock -> {
                compressionConfigProvider?.setBlockSize(command.size)
                state
            }

            is Command.ShowCompressionStatus -> state // Обработка в CliApplication

            // Команды управления стратегиями контекста (для Task 5)
            is Command.ShowStrategyMenu -> {
                handleShowStrategyMenu()
                state
            }

            is Command.SwitchStrategy -> {
                handleSwitchStrategy(command.index)
                state
            }

            is Command.ShowCurrentStrategy -> {
                handleShowCurrentStrategy()
                state
            }

            is Command.CreateBranch -> {
                handleCreateBranch(command.name)
                state
            }

            is Command.SwitchBranch -> {
                handleSwitchBranch(command.name)
                state
            }

            is Command.ListBranches -> {
                handleListBranches()
                state
            }

            is Command.CreateCheckpoint -> {
                handleCreateCheckpoint()
                state
            }

            is Command.ListCheckpoints -> {
                handleListCheckpoints()
                state
            }

            is Command.ListFacts -> {
                handleListFacts()
                state
            }

            is Command.ClearFacts -> {
                handleClearFacts()
                state
            }

            is Command.AddFact -> {
                handleAddFact(command.key, command.value)
                state
            }

            is Command.RemoveFact -> {
                handleRemoveFact(command.key)
                state
            }

            // Пользовательский ввод
            is Command.UserInput -> state
            is Command.Unknown -> state
        }
    }

    // ──── Strategy command handlers ────

    private fun handleShowStrategyMenu() {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        println("📋 Доступные стратегии управления контекстом:")
        manager.listStrategies().forEachIndexed { index, info ->
            val marker = if (info.isCurrent) " ✓" else ""
            println("   ${index + 1}. ${info.name}$marker — ${info.description}")
        }
        println("   Используйте :strategy <номер> для переключения")
    }

    private fun handleSwitchStrategy(index: Int) {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        try {
            manager.switchStrategyByIndex(index)
            val strategy = manager.getCurrentStrategy()
            println("✓ Переключено на стратегию: ${strategy.name}")
        } catch (e: IllegalArgumentException) {
            println("⚠ ${e.message}")
        }
    }

    private fun handleShowCurrentStrategy() {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        println("📌 Текущая стратегия: ${strategy.name}")
        println("   ${strategy.description}")
    }

    private fun handleCreateBranch(name: String) {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is BranchingStrategy) {
            println("⚠ Команда :branch доступна только при стратегии Branching. Используйте :strategy для переключения.")
            return
        }
        // Создаём чекпоинт от текущего состояния и ветку от него
        // Используем пустой Dialog как заглушку, т.к. текущая ветка уже содержит сообщения
        val currentBranch = strategy.getCurrentBranch()
        val dialog = io.averkhogliad.ai.challenge.week1.domain.model.Dialog.create(
            id = currentBranch.dialogId,
            title = "branch-source"
        )
        val checkpoint = strategy.createCheckpoint(dialog, currentBranch.messages.size)
        val branch = strategy.createBranch(name, checkpoint.id)
        println("✓ Ветка '${branch.name}' создана (id: ${branch.id.value})")
    }

    private fun handleSwitchBranch(name: String) {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is BranchingStrategy) {
            println("⚠ Команда :branch доступна только при стратегии Branching. Используйте :strategy для переключения.")
            return
        }
        val branch = strategy.listBranches().find { it.name == name }
        if (branch == null) {
            println("⚠ Ветка '$name' не найдена. Используйте :branch list для списка веток.")
            return
        }
        strategy.switchBranch(branch.id)
        println("✓ Переключено на ветку: ${branch.name}")
    }

    private fun handleListBranches() {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is BranchingStrategy) {
            println("⚠ Команда :branch доступна только при стратегии Branching. Используйте :strategy для переключения.")
            return
        }
        val currentBranch = strategy.getCurrentBranch()
        val branches = strategy.listBranches()
        if (branches.isEmpty()) {
            println("📋 Ветки не найдены")
            return
        }
        println("📋 Ветки диалога:")
        branches.forEach { branch ->
            val marker = if (branch.id == currentBranch.id) " ✓" else ""
            val activeMarker = if (branch.isActive) " [active]" else ""
            println("   • ${branch.name}$marker$activeMarker (сообщений: ${branch.messages.size})")
        }
    }

    private fun handleCreateCheckpoint() {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is BranchingStrategy) {
            println("⚠ Команда :checkpoint доступна только при стратегии Branching. Используйте :strategy для переключения.")
            return
        }
        val currentBranch = strategy.getCurrentBranch()
        val dialog = io.averkhogliad.ai.challenge.week1.domain.model.Dialog.create(
            id = currentBranch.dialogId,
            title = "checkpoint-source"
        )
        val checkpoint = strategy.createCheckpoint(dialog, currentBranch.messages.size)
        println("✓ Чекпоинт создан (id: ${checkpoint.id.value}, сообщений: ${checkpoint.messagesSnapshot.size})")
    }

    private fun handleListCheckpoints() {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is BranchingStrategy) {
            println("⚠ Команда :checkpoint доступна только при стратегии Branching. Используйте :strategy для переключения.")
            return
        }
        val checkpoints = strategy.listCheckpoints()
        if (checkpoints.isEmpty()) {
            println("📋 Чекпоинты не найдены")
            return
        }
        println("📋 Чекпоинты:")
        checkpoints.forEach { cp ->
            println("   • ${cp.id.value} — сообщений: ${cp.messagesSnapshot.size}, создан: ${cp.createdAt}")
        }
    }

    private fun handleListFacts() {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is StickyFactsStrategy) {
            println("⚠ Команда :facts доступна только при стратегии Sticky Facts. Используйте :strategy для переключения.")
            return
        }
        val factsStore = strategy.getFactsStore()
        val facts = factsStore.facts.values
        if (facts.isEmpty()) {
            println("📋 Факты не найдены")
            return
        }
        println("📋 Сохранённые факты (${facts.size}):")
        facts.sortedBy { it.category }.forEach { fact ->
            println("   • [${fact.category.code}] ${fact.key.substringAfter(":")}: ${fact.value}")
        }
    }

    private fun handleClearFacts() {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is StickyFactsStrategy) {
            println("⚠ Команда :facts доступна только при стратегии Sticky Facts. Используйте :strategy для переключения.")
            return
        }
        strategy.clearFacts()
        println("✓ Все факты очищены")
    }

    private fun handleAddFact(key: String, value: String) {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is StickyFactsStrategy) {
            println("⚠ Команда :facts доступна только при стратегии Sticky Facts. Используйте :strategy для переключения.")
            return
        }
        strategy.addFact(key, value, FactCategory.REQUIREMENT)
        println("✓ Факт добавлен: $key = $value")
    }

    private fun handleRemoveFact(key: String) {
        val manager = contextStrategyManager ?: run {
            println("⚠ Менеджер стратегий не инициализирован")
            return
        }
        val strategy = manager.getCurrentStrategy()
        if (strategy !is StickyFactsStrategy) {
            println("⚠ Команда :facts доступна только при стратегии Sticky Facts. Используйте :strategy для переключения.")
            return
        }
        strategy.removeFact(key)
        println("✓ Факт удалён: $key")
    }

    // ──── Dialog command handlers ────

    private suspend fun handleNewDialog(command: Command.NewDialog, state: CliState): CliState {
        val accessor = getDialogManagerAccessor(state) ?: return state
        val dialogId = accessor.createNewDialog(command.title)
        return state.copy(currentDialogId = dialogId)
    }

    private fun handleSwitchDialog(command: Command.SwitchDialog, state: CliState): CliState {
        val accessor = getDialogManagerAccessor(state) ?: return state
        accessor.setCurrentDialog(DialogId(command.id))
        return state.copy(currentDialogId = DialogId(command.id))
    }

    private fun getDialogManagerAccessor(state: CliState): DialogManagerAccessor? {
        val taskId = state.currentTaskId ?: return null
        return executors[TaskId(taskId)] as? DialogManagerAccessor
    }

    suspend fun executeUserInput(command: Command.UserInput, state: CliState): Pair<CliState, TaskResult?> {
        var currentState = state
        var taskId = currentState.currentTaskId

        // Если задача не выбрана, но есть только одна задача — выбираем её автоматически
        // Это позволяет пользователю сразу вводить промпт без предварительного выбора задачи
        if (taskId == null && executors.size == 1) {
            taskId = executors.keys.first().value
            currentState = currentState.copy(currentTaskId = taskId)
        }

        if (taskId == null) return Pair(currentState, null)

        val executor = executors[TaskId(taskId)]
        if (executor == null) return Pair(currentState, null)

        // Для DialogManagerAccessor синхронизируем currentDialogId из состояния
        if (executor is DialogManagerAccessor) {
            currentState.currentDialogId?.let { executor.setCurrentDialog(it) }
        }

        val prompt = Prompt(command.text)
        val result = executor.execute(prompt, currentState.executionConfig)

        // Обновляем currentDialogId из executor (если он создал новый диалог)
        val newState = if (executor is DialogManagerAccessor) {
            currentState.copy(currentDialogId = executor.getCurrentDialogId())
        } else {
            currentState
        }

        return Pair(newState, result)
    }

    fun getExecutor(taskId: TaskId): TaskExecutor? = executors[taskId]

    /**
     * Возвращает executor для текущей задачи из состояния CLI.
     * Используется [CliApplication] для получения [DialogManagerAccessor] без привязки к TaskId(2).
     */
    fun getAccessorForCurrentTask(state: CliState): TaskExecutor? {
        val taskId = state.currentTaskId ?: return null
        return executors[TaskId(taskId)]
    }

    fun getAllExecutors(): List<TaskExecutor> = executors.values.toList()
}
