package io.averkhogliad.ai.challenge.week6.application.mcp

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.error.asFailure
import io.averkhogliad.ai.challenge.week6.domain.port.McpServerRepository
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolRegistry

class ReconnectMcpServerUseCase(
    private val repository: McpServerRepository,
    private val toolRegistry: ToolRegistry,
    private val clientManager: McpClientManager,
) {
    suspend fun execute(name: String): DomainResult<McpServerStatus> {
        val findResult = repository.findByName(name)
        if (findResult.isFailure) return findResult.asFailure()
        val server = findResult.getOrThrow() ?: return DomainResult.Failure(DomainError.mcpServerNotFound(name))

        val status = clientManager.reconnect(name, server)
        if (status != McpServerStatus.CONNECTED) {
            return DomainResult.Failure(DomainError.mcpConnectionFailed(name, "Reconnect failed"))
        }

        // Reregister remote tools
        clientManager.registerRemoteTools(name, toolRegistry)

        return DomainResult.Success(status)
    }
}
