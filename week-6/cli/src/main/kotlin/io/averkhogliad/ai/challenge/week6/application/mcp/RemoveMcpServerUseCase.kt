package io.averkhogliad.ai.challenge.week6.application.mcp

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.error.asFailure
import io.averkhogliad.ai.challenge.week6.domain.port.McpServerRepository
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolRegistry

class RemoveMcpServerUseCase(
    private val repository: McpServerRepository,
    private val toolRegistry: ToolRegistry,
    private val clientManager: McpClientManager,
) {
    suspend fun execute(name: String): DomainResult<Unit> {
        val findResult = repository.findByName(name)
        if (findResult.isFailure) return findResult.asFailure()
        if (findResult.getOrThrow() == null) return DomainResult.Failure(DomainError.mcpServerNotFound(name))

        clientManager.disconnect(name)
        toolRegistry.unregisterRemote(name)
        return repository.deleteByName(name)
    }
}
