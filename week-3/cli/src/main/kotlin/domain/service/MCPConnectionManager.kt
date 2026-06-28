package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult

interface MCPConnectionManager {
    suspend fun connect(serverId: ModelId): MCPConnectionState
    suspend fun disconnect(serverId: ModelId)
    fun getStatus(serverId: ModelId): MCPConnectionState
    suspend fun getTools(serverId: ModelId): List<MCPTool>
    suspend fun callTool(serverId: ModelId, name: String, arguments: Map<String, Any?>): CallToolResult
    fun isConnected(serverId: ModelId): Boolean
}
