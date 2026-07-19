package io.averkhogliad.ai.challenge.week6.cli.handlers.mcp

import io.averkhogliad.ai.challenge.week6.application.mcp.ReconnectMcpServerUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class McpReconnectHandler(
    private val reconnectMcpServerUseCase: ReconnectMcpServerUseCase,
) : CommandHandler {
    override val name = "/mcp reconnect"
    override val description = "Переподключить MCP-сервер: /mcp reconnect <name>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/mcp reconnect" || rawInput.startsWith("/mcp reconnect ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val name = rawInput.removePrefix("/mcp reconnect").trim()
        if (name.isEmpty()) {
            return CommandEffect.Print("Использование: /mcp reconnect <name>", isError = true)
        }

        return when (val result = reconnectMcpServerUseCase.execute(name)) {
            is DomainResult.Success -> CommandEffect.Print("✓ Переподключено к '$name'")
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }
}
