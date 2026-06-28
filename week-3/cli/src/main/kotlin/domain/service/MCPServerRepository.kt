package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPServerConfig

interface MCPServerRepository {
    suspend fun save(config: MCPServerConfig): MCPServerConfig
    suspend fun findById(id: ModelId): MCPServerConfig?
    suspend fun findByName(name: String): MCPServerConfig?
    suspend fun findAll(): List<MCPServerConfig>
    suspend fun delete(id: ModelId)
    suspend fun existsByName(name: String): Boolean
}
