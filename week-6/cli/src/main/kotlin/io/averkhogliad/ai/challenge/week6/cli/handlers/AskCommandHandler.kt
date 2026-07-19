package io.averkhogliad.ai.challenge.week6.cli.handlers

import io.averkhogliad.ai.challenge.week6.application.AgentLoopService
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import kotlinx.coroutines.flow.Flow

class AskCommandHandler(
    private val agentLoopService: AgentLoopService,
) : CommandHandler {

    override val name: String = "/ask"
    override val description: String = "Задать вопрос о проекте: /ask <вопрос>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/ask" || rawInput.startsWith("/ask ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val query = rawInput.removePrefix("/ask").trim()

        if (query.isEmpty()) {
            return CommandEffect.Print("Использование: /ask <вопрос>", isError = true)
        }

        val flow: Flow<String> = agentLoopService.processQuery(query)

        return CommandEffect.StreamOutput(flow)
    }
}
