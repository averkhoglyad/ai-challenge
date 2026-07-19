package io.averkhogliad.ai.challenge.week6.cli.handlers.mcp

import io.averkhogliad.ai.challenge.week6.application.mcp.McpClientManager
import io.averkhogliad.ai.challenge.week6.application.mcp.McpServerStatus
import io.averkhogliad.ai.challenge.week6.cli.rendering.McpServerInfo
import io.averkhogliad.ai.challenge.week6.cli.rendering.McpServerInfoRenderer
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.McpServerRepository
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class McpListHandler(
    private val repository: McpServerRepository,
    private val clientManager: McpClientManager,
    private val renderer: McpServerInfoRenderer,
) : CommandHandler {
    override val name = "/mcp list"
    override val description = "Список всех MCP-серверов"

    override suspend fun execute(rawInput: String): CommandEffect {
        val serversResult = repository.findAll()
        return when (serversResult) {
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(serversResult.error)
            is DomainResult.Success -> {
                val servers = serversResult.value
                if (servers.isEmpty()) {
                    CommandEffect.Print("Нет MCP-серверов. Добавьте: /mcp add <name> <url>")
                } else {
                    val infos = servers.map { server ->
                        val status = clientManager.getStatus(server.name)
                        val toolsDisplay = when (val toolsResult = clientManager.listTools(server.name)) {
                            is DomainResult.Success -> toolsResult.value.size.toString()
                            is DomainResult.Failure -> "—"
                        }
                        McpServerInfo(
                            name = server.name,
                            type = server.serverType.value,
                            status = when (status) {
                                McpServerStatus.CONNECTED -> "connected"
                                McpServerStatus.ERROR -> "error"
                                McpServerStatus.DISABLED -> "disabled"
                                McpServerStatus.DISCONNECTED -> "disconnected"
                            },
                            toolsCount = toolsDisplay,
                        )
                    }
                    CommandEffect.Print(renderer.render(infos))
                }
            }
        }
    }
}
