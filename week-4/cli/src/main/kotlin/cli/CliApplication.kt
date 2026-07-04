package io.averkhogliad.ai.challenge.week4.cli.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatModeHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandParser
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.CommandHandler
import kotlinx.coroutines.runBlocking

class CliApplication(
    private val renderer: CliRenderer = ConsoleCliRenderer(),
    private val input: CliInput = ConsoleCliInput(),
    private val dispatcher: CliCommandDispatcher,
    private val commandHandler: CommandHandler,
    private val applicationResources: AutoCloseable,
    private val chatModeHandler: ChatModeHandler? = null,
    private val initialState: CliState = CliState(),
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
        var state = initialState

        renderer.renderWelcome()
        renderer.renderMenu(commandHandler.getAllExecutors())

        while (state.isRunning) {
            try {
                if (state.chatMode && chatModeHandler != null) {
                    state = chatModeRepl(state)
                } else {
                    renderer.renderPrompt(state)
                    val rawInput = input.readLine() ?: break
                    val context = buildCommandContext(state)
                    val command = CommandParser.parse(rawInput, context)
                    state = dispatcher.handle(command, state)
                }
            } catch (e: Exception) {
                renderer.renderError("Неожиданная ошибка: ${e.message}")
            }
        }

        renderer.renderGoodbye()
    }

    private suspend fun chatModeRepl(state: CliState): CliState {
        var currentState = state
        val handler = chatModeHandler!!

        // При первом входе в chat mode: создать/активировать сессию
        if (currentState.activeChatSessionId == null) {
            currentState = handler.enterChatMode(currentState)
        }

        while (currentState.isRunning && currentState.chatMode) {
            try {
                val prompt = handler.getPrompt(currentState)
                print(prompt)
                val rawInput = input.readLine() ?: return currentState.copy(isRunning = false)

                if (rawInput.isBlank()) continue

                currentState = if (rawInput.startsWith(":")) {
                    val parts = rawInput.removePrefix(":").split(" ", limit = 2)
                    val commandName = parts[0].lowercase()
                    val args = parts.getOrElse(1) { "" }.trim()

                    when (commandName) {
                        "back", "b", "exit" -> handler.exitChatMode(currentState)
                        "quit", "q" -> currentState.copy(isRunning = false)
                        "clear" -> {
                            val sessionIdStr = currentState.activeChatSessionId
                            if (sessionIdStr != null) {
                                try {
                                    val sessionId = java.util.UUID.fromString(sessionIdStr)
                                    handler.clearHistory(sessionId)
                                } catch (e: Exception) {
                                    renderer.renderError("Ошибка при очистке истории: ${e.message}")
                                }
                            }
                            renderer.renderInfo("История очищена (память задачи сохранена)")
                            currentState
                        }

                        else -> handler.handleCommand(commandName, args, currentState)
                    }
                } else {
                    handler.handleMessage(rawInput, currentState)
                }
            } catch (e: Exception) {
                renderer.renderError("Неожиданная ошибка: ${e.message}")
            }
        }
        return currentState
    }

    private fun buildCommandContext(state: CliState): CommandContext {
        val isTaskActive = state.currentTaskId != null || state.currentTodoTaskId != null || state.taskListMode
        if (!isTaskActive) {
            return CommandContext.TASK_SELECTION
        }

        return CommandContext.activeTaskContext(state.currentTaskId ?: 1)

    }
}
