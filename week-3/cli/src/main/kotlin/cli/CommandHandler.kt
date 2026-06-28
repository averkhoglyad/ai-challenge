package io.averkhogliad.ai.challenge.week3.cli.cli

import io.averkhogliad.ai.challenge.week3.cli.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId

class CommandHandler(
    private val executors: Map<TaskId, TaskExecutor>,
) {
    fun handle(command: Command, state: CliState): CliState = when (command) {
        is Command.Quit -> state.copy(isRunning = false)
        is Command.SelectTask -> state.copy(currentTaskId = command.taskId)
        is Command.SetTemperature -> state.copy(
            executionConfig = state.executionConfig.copy(temperature = command.value)
        )

        is Command.SetMaxTokens -> state.copy(
            executionConfig = state.executionConfig.copy(maxTokens = command.value)
        )

        is Command.SetStopSequences -> state.copy(
            executionConfig = state.executionConfig.copy(stopSequences = command.values)
        )

        is Command.ResetParameters -> state.copy(executionConfig = TaskExecutionConfig())
        else -> state
    }

    suspend fun executeUserInput(command: Command.UserInput, state: CliState): Pair<CliState, TaskResult?> {
        var currentState = state
        var taskId = currentState.currentTaskId

        if (taskId == null && executors.size == 1) {
            taskId = executors.keys.first().value.toInt()
            currentState = currentState.copy(currentTaskId = taskId)
        }

        if (taskId == null) return Pair(currentState, null)

        val executor = executors[TaskId(taskId.toString())] ?: return Pair(currentState, null)
        val result = executor.execute(Prompt(command.text), currentState.executionConfig)
        return Pair(currentState, result)
    }

    fun getExecutor(taskId: TaskId): TaskExecutor? = executors[taskId]

    fun getAllExecutors(): List<TaskExecutor> = executors.values.toList()
}
