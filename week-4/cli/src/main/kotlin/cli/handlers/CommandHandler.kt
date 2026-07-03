package io.averkhogliad.ai.challenge.week4.cli.cli.handlers

import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig

class CommandHandler {
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
}
