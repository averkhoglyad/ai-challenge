package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPServerConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool

interface MCPClient {
    suspend fun connect(config: MCPServerConfig): MCPConnectionState
    suspend fun disconnect()
    suspend fun listTools(): List<MCPTool>
    fun isConnected(): Boolean
    fun getStatus(): MCPConnectionState
}
