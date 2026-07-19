package io.averkhogliad.ai.challenge.week6.cli.contexts

import io.averkhogliad.ai.challenge.week6.application.SupportUseCase
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.core.DefaultInputHandler
import io.averkhogliad.cli.repl.core.ReplContext

class SupportContext(
    private val supportUseCase: SupportUseCase,
) : ReplContext {
    override val name = "support"
    override val prompt = "support> "
    override val handlers: List<CommandHandler> = listOf(
        object : CommandHandler {
            override val name = "/back"
            override val description = "Вернуться в copilot"
            override suspend fun execute(rawInput: String) = CommandEffect.GoBack
        },
    )
    override val defaultHandler = object : DefaultInputHandler {
        override val description: String = "Задать вопрос поддержке"
        override suspend fun handle(rawInput: String): CommandEffect {
            return CommandEffect.StreamOutput(supportUseCase.execute(rawInput))
        }
    }
}
