package io.averkhogliad.cli.repl.engine

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class QuitHandler : CommandHandler {
    override val name: String = "/quit"
    override val description: String = "Exit the REPL"

    override suspend fun execute(rawInput: String): CommandEffect = CommandEffect.Exit
}
