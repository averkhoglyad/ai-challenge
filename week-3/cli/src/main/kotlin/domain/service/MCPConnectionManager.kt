package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool

interface MCPConnectionManager {
    suspend fun connect(serverId: ModelId): MCPConnectionState
    suspend fun disconnect(serverId: ModelId)
    fun getStatus(serverId: ModelId): MCPConnectionState
    suspend fun getTools(serverId: ModelId): List<MCPTool>
    fun isConnected(serverId: ModelId): Boolean
}
