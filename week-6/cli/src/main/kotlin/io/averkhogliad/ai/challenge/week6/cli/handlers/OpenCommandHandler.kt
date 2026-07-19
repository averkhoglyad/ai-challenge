package io.averkhogliad.ai.challenge.week6.cli.handlers

import io.averkhogliad.ai.challenge.week6.application.OpenProjectUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class OpenCommandHandler(
    private val openProjectUseCase: OpenProjectUseCase,
) : CommandHandler {

    override val name: String = "/open"
    override val description: String = "Открыть проект: /open <path>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/open" || rawInput.startsWith("/open ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val path = rawInput.removePrefix("/open").trim()

        if (path.isEmpty()) {
            return CommandEffect.Print("Использование: /open <path>", isError = true)
        }

        return when (val result = openProjectUseCase.execute(path)) {
            is DomainResult.Success -> CommandEffect.Navigate("copilot")
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }
}
