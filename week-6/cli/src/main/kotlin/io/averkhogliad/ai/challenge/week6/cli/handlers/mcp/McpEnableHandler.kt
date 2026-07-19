package io.averkhogliad.ai.challenge.week6.cli.handlers.mcp

import io.averkhogliad.ai.challenge.week6.application.mcp.ToggleMcpServerUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class McpEnableHandler(
    private val toggleMcpServerUseCase: ToggleMcpServerUseCase,
) : CommandHandler {
    override val name = "/mcp enable"
    override val aliases = listOf("/mcp disable")
    override val description = "Включить/выключить MCP-сервер: /mcp enable|disable <name>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/mcp enable" || rawInput == "/mcp disable" ||
                rawInput.startsWith("/mcp enable ") || rawInput.startsWith("/mcp disable ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val isEnable = rawInput.startsWith("/mcp enable")
        val prefix = if (isEnable) "/mcp enable" else "/mcp disable"
        val name = rawInput.removePrefix(prefix).trim()
        if (name.isEmpty()) {
            return CommandEffect.Print("Использование: $prefix <name>", isError = true)
        }

        return when (val result = toggleMcpServerUseCase.execute(name, isEnable)) {
            is DomainResult.Success -> {
                val action = if (isEnable) "включён" else "выключен"
                CommandEffect.Print("✓ Сервер '$name' $action")
            }

            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }
}
