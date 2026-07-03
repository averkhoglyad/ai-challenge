package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult

interface MCPConnectionManager {
    suspend fun connect(serverId: ModelId): MCPConnectionState
    suspend fun connectSystem(id: ModelId, name: String, transport: MCPTransport): MCPConnectionState
    suspend fun disconnect(serverId: ModelId)
    fun getStatus(serverId: ModelId): MCPConnectionState
    suspend fun getTools(serverId: ModelId): List<MCPTool>
    suspend fun callTool(serverId: ModelId, name: String, arguments: Map<String, Any?>): CallToolResult
    suspend fun getPrompts(serverId: ModelId): List<McpPrompt>
    suspend fun getPrompt(serverId: ModelId, name: String, arguments: Map<String, String>): List<McpPromptMessage>
    fun isConnected(serverId: ModelId): Boolean
    fun getSystemServerIds(): Set<ModelId>
}
