package io.averkhogliad.cli.repl.engine

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.dispatcher.ContextStack

class BackHandler(private val contextStack: ContextStack) : CommandHandler {
    override val name: String = "/back"
    override val aliases: List<String> = listOf("/b")
    override val description: String = "Return to the previous context"

    override suspend fun execute(rawInput: String): CommandEffect {
        return if (contextStack.chain().size <= 1) {
            CommandEffect.Print("Already at the root context, nowhere to go back.", isError = true)
        } else {
            CommandEffect.GoBack
        }
    }
}
