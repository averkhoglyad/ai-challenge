package io.averkhogliad.ai.challenge.week6.cli.handlers.mcp

import io.averkhogliad.ai.challenge.week6.application.mcp.AddMcpServerUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class McpAddHandler(
    private val addMcpServerUseCase: AddMcpServerUseCase,
) : CommandHandler {
    override val name = "/mcp add"
    override val description = "Добавить MCP-сервер: /mcp add <name> <url>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/mcp add" || rawInput.startsWith("/mcp add ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val args = rawInput.removePrefix("/mcp add").trim().split(" ", limit = 2)
        if (args.size < 2 || args[0].isEmpty() || args[1].isEmpty()) {
            return CommandEffect.Print("Использование: /mcp add <name> <url>", isError = true)
        }

        val name = args[0]
        val url = args[1]

        return when (val result = addMcpServerUseCase.execute(name, url)) {
            is DomainResult.Success -> {
                val server = result.value
                CommandEffect.Print("✓ Подключено к '$name'. URL: ${server.baseUrl}")
            }

            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }
}
