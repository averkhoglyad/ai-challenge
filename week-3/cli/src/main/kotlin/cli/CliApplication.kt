package io.averkhogliad.ai.challenge.week3.cli.cli

import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.CommandParser
import io.averkhogliad.ai.challenge.week3.cli.cli.handlers.CommandHandler
import kotlinx.coroutines.runBlocking

class CliApplication(
    private val renderer: CliRenderer = ConsoleCliRenderer(),
    private val input: CliInput = ConsoleCliInput(),
    private val dispatcher: CliCommandDispatcher,
    private val commandHandler: CommandHandler,
    private val applicationResources: AutoCloseable,
) : AutoCloseable {

    fun run() {
        runBlocking {
            repl()
        }
    }

    override fun close() {
        try {
            applicationResources.close()
        } catch (e: Exception) {
            System.err.println("Предупреждение: не удалось освободить ресурсы приложения: ${e.message}")
        }
    }

    private suspend fun repl() {
        var state = CliState()

        renderer.renderWelcome()
        renderer.renderMenu(commandHandler.getAllExecutors())

        while (state.isRunning) {
            try {
                renderer.renderPrompt(state)
                val rawInput = input.readLine() ?: break
                val context = buildCommandContext(state)
                val command = CommandParser.parse(rawInput, context)
                state = dispatcher.handle(command, state)
            } catch (e: Exception) {
                renderer.renderError("Неожиданная ошибка: ${e.message}")
            }
        }

        renderer.renderGoodbye()
    }

    private fun buildCommandContext(state: CliState): CommandContext {
        val isTaskActive = state.currentTaskId != null || state.currentTodoTaskId != null || state.taskListMode
        if (!isTaskActive) {
            return CommandContext.TASK_SELECTION
        }

        return CommandContext.activeTaskContext(state.currentTaskId ?: 1)

    }
}
