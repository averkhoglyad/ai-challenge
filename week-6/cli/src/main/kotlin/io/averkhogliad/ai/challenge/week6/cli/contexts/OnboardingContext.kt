package io.averkhogliad.ai.challenge.week6.cli.contexts

import io.averkhogliad.ai.challenge.week6.application.OpenProjectUseCase
import io.averkhogliad.ai.challenge.week6.cli.handlers.OpenCommandHandler
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.core.DefaultInputHandler
import io.averkhogliad.cli.repl.core.ReplContext

class OnboardingContext(
    private val openProjectUseCase: OpenProjectUseCase,
) : ReplContext {

    override val name: String = "onboarding"
    override val prompt: String = "onboarding> "

    override val handlers: List<CommandHandler> = listOf(
        OpenCommandHandler(openProjectUseCase),
    )

    override val defaultHandler: DefaultInputHandler = object : DefaultInputHandler {
        override val description: String = "Подсказка для нового пользователя"
        override suspend fun handle(rawInput: String): CommandEffect =
            CommandEffect.Print("Сначала откройте проект: /open <path>")
    }
}
