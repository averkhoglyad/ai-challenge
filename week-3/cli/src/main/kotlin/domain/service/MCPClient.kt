package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPServerConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult

interface MCPClient {
    suspend fun connect(config: MCPServerConfig): MCPConnectionState
    suspend fun disconnect()
    suspend fun listTools(): List<MCPTool>
    suspend fun callTool(name: String, arguments: Map<String, Any?>): CallToolResult
    fun isConnected(): Boolean
    fun getStatus(): MCPConnectionState
}
