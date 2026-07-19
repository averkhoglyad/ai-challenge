package io.averkhogliad.ai.challenge.week6.cli.handlers.mcp

import io.averkhogliad.ai.challenge.week6.application.mcp.McpClientManager
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.McpServerRepository
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class McpInfoHandler(
    private val repository: McpServerRepository,
    private val clientManager: McpClientManager,
) : CommandHandler {
    override val name = "/mcp info"
    override val description = "Информация о MCP-сервере: /mcp info <name>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/mcp info" || rawInput.startsWith("/mcp info ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val name = rawInput.removePrefix("/mcp info").trim()
        if (name.isEmpty()) {
            return CommandEffect.Print("Использование: /mcp info <name>", isError = true)
        }

        val findResult = repository.findByName(name)
        return when (findResult) {
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(findResult.error)
            is DomainResult.Success -> {
                val server = findResult.value
                    ?: return CommandEffect.Print("Сервер '$name' не найден", isError = true)

                val status = clientManager.getStatus(name)
                val toolsResult = clientManager.listTools(name)

                val info = buildString {
                    appendLine("Name: ${server.name}")
                    appendLine("URL: ${server.baseUrl ?: "N/A"}")
                    appendLine("Type: ${server.serverType.value}")
                    appendLine("Status: ${status.name.lowercase()}")
                    appendLine("Enabled: ${server.enabled}")
                    when (toolsResult) {
                        is DomainResult.Success -> {
                            val tools = toolsResult.value
                            appendLine("Tools (${tools.size}):")
                            tools.forEach { tool ->
                                appendLine("  - ${tool.name}: ${tool.description ?: "N/A"}")
                            }
                        }

                        is DomainResult.Failure -> {
                            appendLine("Tools: (недоступно)")
                        }
                    }
                }

                CommandEffect.Print(info)
            }
        }
    }
}
