package io.averkhogliad.ai.challenge.week6.cli.handlers

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class SupportCommandHandler : CommandHandler {
    override val name = "/support"
    override val description = "Перейти в контекст поддержки"
    override suspend fun execute(rawInput: String) = CommandEffect.Navigate("support")
}
