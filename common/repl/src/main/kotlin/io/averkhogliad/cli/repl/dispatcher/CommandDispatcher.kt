package io.averkhogliad.cli.repl.dispatcher

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class CommandDispatcher(
    private val contextStack: ContextStack,
    private val builtinHandlers: List<CommandHandler> = emptyList()
) {
    suspend fun dispatch(rawInput: String): CommandEffect {
        if (rawInput.startsWith("/")) {
            for (context in contextStack.chain()) {
                val handler = context.handlers.find { it.canHandle(rawInput) }
                if (handler != null) {
                    return handler.execute(rawInput)
                }
            }

            val builtin = builtinHandlers.find { it.canHandle(rawInput) }
            if (builtin != null) {
                return builtin.execute(rawInput)
            }

            contextStack.current.defaultHandler?.let { default ->
                return default.handle(rawInput)
            }

            return CommandEffect.Print("Unknown command: $rawInput", isError = true)
        }

        contextStack.current.defaultHandler?.let { default ->
            return default.handle(rawInput)
        }

        return CommandEffect.Print("Unknown command: $rawInput", isError = true)
    }
}
