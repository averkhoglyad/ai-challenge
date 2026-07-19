package io.averkhogliad.ai.challenge.week6.application.mcp

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.error.asFailure
import io.averkhogliad.ai.challenge.week6.domain.port.McpServerRepository
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolRegistry

class ToggleMcpServerUseCase(
    private val repository: McpServerRepository,
    private val clientManager: McpClientManager,
    private val toolRegistry: ToolRegistry,
) {
    suspend fun execute(name: String, enabled: Boolean): DomainResult<Unit> {
        val findResult = repository.findByName(name)
        if (findResult.isFailure) return findResult.asFailure()
        val server = findResult.getOrThrow() ?: return DomainResult.Failure(DomainError.mcpServerNotFound(name))
        if (!enabled) {
            clientManager.disconnect(name)
            toolRegistry.unregisterRemote(name)
        } else {
            val status = clientManager.reconnect(name, server)
            if (status != McpServerStatus.CONNECTED) {
                return DomainResult.Failure(DomainError.mcpConnectionFailed(name, "Reconnect failed"))
            }
            clientManager.registerRemoteTools(name, toolRegistry)
        }

        return repository.updateEnabled(name, enabled)
    }
}
