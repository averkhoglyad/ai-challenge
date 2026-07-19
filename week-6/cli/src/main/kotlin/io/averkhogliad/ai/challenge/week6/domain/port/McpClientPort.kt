package io.averkhogliad.ai.challenge.week6.domain.port

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.McpServer
import kotlinx.serialization.json.JsonObject

data class McpToolDefinition(
    val name: String,
    val description: String?,
    val inputSchema: JsonObject,
)

interface McpClientPort {
    suspend fun connect(server: McpServer): DomainResult<Unit>
    suspend fun disconnect()
    suspend fun listTools(): DomainResult<List<McpToolDefinition>>
    suspend fun callTool(name: String, arguments: JsonObject): DomainResult<String>
    fun isConnected(): Boolean
}
