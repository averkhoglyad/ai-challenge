package io.averkhogliad.ai.challenge.week4.cli.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatModeHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandParser
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.CommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.indexer.IndexCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandParser
import kotlinx.coroutines.runBlocking

class CliApplication(
    private val renderer: CliRenderer = ConsoleCliRenderer(),
    private val input: CliInput = ConsoleCliInput(),
    private val dispatcher: CliCommandDispatcher,
    private val commandHandler: CommandHandler,
    private val applicationResources: AutoCloseable,
    private val chatModeHandler: ChatModeHandler? = null,
    private val ragCommandHandler: RagCommandHandler? = null,
    private val indexCommandHandler: IndexCommandHandler? = null,
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
            try {
                currentState = handler.enterChatMode(currentState)
            } catch (e: Exception) {
                renderer.renderError("Не удалось войти в режим чата: ${e.message}")
                return currentState.copy(chatMode = false)
            }
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
                        "new" -> handler.handleCommand("chat-new", "", currentState)
                        "rag" -> handleRagCommand(args, currentState)
                        "index-switch" -> handleIndexSwitch(args, currentState)
                        "index-runs" -> handleIndexRuns(currentState)
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

                        else -> {
                            // Пробуем диспетчер (глобальные команды: help, params, status, ...)
                            val context = buildCommandContext(currentState)
                            val command = CommandParser.parse(rawInput, context)
                            if (command is Command.Unknown) {
                                handler.handleCommand(commandName, args, currentState)
                            } else {
                                dispatcher.handle(command, currentState)
                            }
                        }
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

    private fun handleRagCommand(args: String, state: CliState): CliState {
        val handler = ragCommandHandler ?: run {
            renderer.renderError("RAG недоступен (LLM не настроен)")
            return state
        }
        val fullInput = if (args.isEmpty()) ":rag" else ":rag $args"
        val command = RagCommandParser.parse(fullInput)
        return if (command != null) {
            handler.handle(command, state)
        } else {
            renderer.renderError("Неизвестная RAG-команда: $fullInput")
            state
        }
    }

    private fun handleIndexSwitch(args: String, state: CliState): CliState {
        val handler = indexCommandHandler ?: run {
            renderer.renderError("Индексация недоступна")
            return state
        }
        if (args.isEmpty()) {
            renderer.renderError("Укажите run ID: :index-switch <runId>")
            return state
        }
        return handler.handleIndexSwitch(
            io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command.IndexSwitch(args),
            state
        )
    }

    private fun handleIndexRuns(state: CliState): CliState {
        val handler = indexCommandHandler ?: run {
            renderer.renderError("Индексация недоступна")
            return state
        }
        return handler.handleIndexRuns(state)
    }
}
