package io.averkhogliad.ai.challenge.week6.cli.handlers.mcp

import io.averkhogliad.ai.challenge.week6.application.mcp.RemoveMcpServerUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class McpRemoveHandler(
    private val removeMcpServerUseCase: RemoveMcpServerUseCase,
) : CommandHandler {
    override val name = "/mcp remove"
    override val description = "Удалить MCP-сервер: /mcp remove <name>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/mcp remove" || rawInput.startsWith("/mcp remove ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val name = rawInput.removePrefix("/mcp remove").trim()
        if (name.isEmpty()) {
            return CommandEffect.Print("Использование: /mcp remove <name>", isError = true)
        }

        return when (val result = removeMcpServerUseCase.execute(name)) {
            is DomainResult.Success -> CommandEffect.Print("✓ Сервер '$name' удалён")
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }
}
