package io.averkhogliad.ai.challenge.week0.cli

import io.averkhogliad.ai.challenge.week0.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week0.cli.commands.Command
import io.averkhogliad.ai.challenge.week0.cli.commands.CommandParser
import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.Task3Config
import io.averkhogliad.ai.challenge.week0.domain.config.Task3Mode

/**
 * Обработчик команд CLI — чистая функция `Command × CliState → CliState`.
 *
 * Не содержит бизнес-логики — только маршрутизация команд:
 * - Глобальные команды (Help, Back, Quit, SelectTask) → изменение [CliState]
 * - Команды параметров (SetTemperature, SetMaxTokens, etc.) → обновление [CliState.executionConfig]
 * - Команды Task3 (SetMode, SetStep, SetExperts, etc.) → обновление [executionConfig.task3]
 * - Команды Task5 (SetModels, ShowModels) → обновление/отображение
 * - [Command.UserInput] → делегирование в [TaskExecutor.execute]
 *
 * Строковые литералы "on"/"off", "direct"/"experts" преобразуются в
 * типизированные значения на уровне [CommandParser]. [CommandHandler]
 * работает исключительно с Boolean, [Task3Mode] и другими типизированными значениями.
 */
class CommandHandler(
    private val executors: Map<TaskId, TaskExecutor>
) {

    suspend fun handle(command: Command, state: CliState): CliState {
        return when (command) {
            // Глобальные команды
            is Command.Help -> state
            is Command.Back -> state.copy(currentTaskId = null)
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
                executionConfig = io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig()
            )

            is Command.ShowParameters -> state

            // Task3 команды — обновление executionConfig.task3
            is Command.SetMode -> state.copy(
                executionConfig = state.executionConfig.copy(
                    task3 = state.executionConfig.task3.copy(mode = command.mode)
                )
            )

            is Command.SetStep -> state.copy(
                executionConfig = state.executionConfig.copy(
                    task3 = state.executionConfig.task3.copy(
                        stepEnabled = command.enabled,
                        stepInstruction = if (command.enabled) Task3Config.DEFAULT_STEP_INSTRUCTION else null
                    )
                )
            )

            is Command.SetMeta -> state.copy(
                executionConfig = state.executionConfig.copy(
                    task3 = state.executionConfig.task3.copy(
                        metaEnabled = command.enabled
                    )
                )
            )

            is Command.SetRole -> state.copy(
                executionConfig = state.executionConfig.copy(
                    task3 = state.executionConfig.task3.copy(role = command.role)
                )
            )

            is Command.SetExperts -> state.copy(
                executionConfig = state.executionConfig.copy(
                    task3 = state.executionConfig.task3.copy(experts = command.experts)
                )
            )

            is Command.ToggleSummary -> state.copy(
                executionConfig = state.executionConfig.copy(
                    task3 = state.executionConfig.task3.copy(summary = command.value)
                )
            )

            is Command.ShowConfig -> state

            // Task5 команды
            is Command.SetModels -> state.copy(task5SelectedModels = command.modelIndices)
            is Command.ShowModels -> state

            // Пользовательский ввод
            is Command.UserInput -> state
            is Command.Unknown -> state
        }
    }

    suspend fun executeUserInput(command: Command.UserInput, state: CliState): Pair<CliState, TaskResult?> {
        val taskId = state.currentTaskId
        if (taskId == null) return Pair(state, null)

        val executor = executors[TaskId(taskId)]
        if (executor == null) return Pair(state, null)

        val prompt = Prompt(command.text)
        val result = executor.execute(prompt, state.executionConfig)
        return Pair(state, result)
    }

    fun getExecutor(taskId: TaskId): TaskExecutor? = executors[taskId]

    fun getAllExecutors(): List<TaskExecutor> = executors.values.toList()
}
