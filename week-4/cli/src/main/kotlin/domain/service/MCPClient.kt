package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult

interface MCPClient {
    suspend fun connect(config: MCPServerConfig): MCPConnectionState
    suspend fun disconnect()
    suspend fun listTools(): List<MCPTool>
    suspend fun callTool(name: String, arguments: Map<String, Any?>): CallToolResult
    suspend fun listPrompts(): List<McpPrompt>
    suspend fun getPrompt(name: String, arguments: Map<String, String>): List<McpPromptMessage>
    fun isConnected(): Boolean
    fun getStatus(): MCPConnectionState
}
