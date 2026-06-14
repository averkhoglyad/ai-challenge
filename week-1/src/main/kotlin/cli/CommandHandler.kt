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
    private val compressionConfigProvider: ContextCompressionConfigProvider? = null
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

            // Пользовательский ввод
            is Command.UserInput -> state
            is Command.Unknown -> state
        }
    }

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
