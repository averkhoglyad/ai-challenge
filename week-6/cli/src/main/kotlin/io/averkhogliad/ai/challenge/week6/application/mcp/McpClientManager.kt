package io.averkhogliad.ai.challenge.week6.application.mcp

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.McpServer
import io.averkhogliad.ai.challenge.week6.domain.port.McpClientPort
import io.averkhogliad.ai.challenge.week6.domain.port.McpToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolRegistry
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolSource
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

enum class McpServerStatus { CONNECTED, ERROR, DISABLED, DISCONNECTED }

class McpClientManager(
    private val clientFactory: () -> McpClientPort,
) {
    private val clients = ConcurrentHashMap<String, McpClientPort>()
    private val statuses = ConcurrentHashMap<String, McpServerStatus>()

    suspend fun connect(server: McpServer): McpServerStatus {
        val client = clientFactory()
        val result = client.connect(server)
        return if (result.isSuccess) {
            clients[server.name]?.disconnect()
            clients[server.name] = client
            statuses[server.name] = McpServerStatus.CONNECTED
            McpServerStatus.CONNECTED
        } else {
            statuses[server.name] = McpServerStatus.ERROR
            McpServerStatus.ERROR
        }
    }

    suspend fun disconnect(serverName: String) {
        try {
            clients[serverName]?.disconnect()
        } catch (_: Exception) {
            // Ignore errors during disconnect
        } finally {
            clients.remove(serverName)
            statuses[serverName] = McpServerStatus.DISCONNECTED
        }
    }

    suspend fun reconnect(serverName: String, server: McpServer): McpServerStatus {
        disconnect(serverName)
        return connect(server)
    }

    fun getStatus(serverName: String): McpServerStatus =
        statuses[serverName] ?: McpServerStatus.DISCONNECTED

    suspend fun listTools(serverName: String): DomainResult<List<McpToolDefinition>> {
        val client = clients[serverName] ?: return DomainResult.Failure(
            DomainError.mcpServerNotFound(serverName)
        )
        return client.listTools()
    }

    suspend fun callTool(
        serverName: String,
        toolName: String,
        arguments: JsonObject,
    ): DomainResult<String> {
        val client = clients[serverName] ?: return DomainResult.Failure(
            DomainError.mcpServerNotFound(serverName)
        )
        return client.callTool(toolName, arguments)
    }

    suspend fun registerRemoteTools(serverName: String, toolRegistry: ToolRegistry) {
        val toolsResult = listTools(serverName)
        if (toolsResult.isSuccess) {
            toolRegistry.unregisterRemote(serverName)
            val remoteTools = toolsResult.getOrThrow().map { toolDef ->
                RemoteToolAdapter(
                    { args ->
                        val callResult = callTool(serverName, toolDef.name, args)
                        when (callResult) {
                            is DomainResult.Success -> ToolResult.Success(callResult.value)
                            is DomainResult.Failure -> ToolResult.Error(callResult.error.message)
                        }
                    },
                    ToolDefinition(
                        name = toolDef.name,
                        description = toolDef.description ?: "",
                        inputSchema = toolDef.inputSchema,
                        source = ToolSource.Remote(serverName),
                    ),
                )
            }
            toolRegistry.registerRemote(serverName, remoteTools)
        } else {
            System.err.println("[McpClientManager] Failed to list tools for '$serverName'")
        }
    }

    suspend fun closeAll() {
        clients.keys.toList().forEach { disconnect(it) }
    }
}
