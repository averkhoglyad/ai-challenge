package io.averkhogliad.ai.challenge.week6.application.mcp

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.error.asFailure
import io.averkhogliad.ai.challenge.week6.domain.model.McpServer
import io.averkhogliad.ai.challenge.week6.domain.model.McpServerType
import io.averkhogliad.ai.challenge.week6.domain.port.McpServerRepository
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolRegistry
import java.time.Instant
import java.util.*

class AddMcpServerUseCase(
    private val repository: McpServerRepository,
    private val toolRegistry: ToolRegistry,
    private val clientManager: McpClientManager,
) {
    suspend fun execute(name: String, url: String): DomainResult<McpServer> {
        if (name.isBlank()) {
            return DomainResult.Failure(DomainError.repository("Server name must not be blank"))
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return DomainResult.Failure(DomainError.invalidUrl(url))
        }

        val server = McpServer(
            id = UUID.randomUUID().toString(),
            name = name,
            serverType = McpServerType.HTTP_SSE,
            baseUrl = url,
            enabled = true,
            createdAt = Instant.now(),
        )

        val saveResult = repository.save(server)
        if (saveResult.isFailure) return saveResult.asFailure()

        val status = clientManager.connect(server)
        if (status == McpServerStatus.ERROR) {
            return DomainResult.Failure(DomainError.mcpConnectionFailed(name, "Connection failed"))
        }

        // Register remote tools
        clientManager.registerRemoteTools(name, toolRegistry)

        return DomainResult.Success(server)
    }
}
