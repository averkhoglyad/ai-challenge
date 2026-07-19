package io.averkhogliad.ai.challenge.week6.domain.port

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.McpServer

interface McpServerRepository {
    suspend fun save(server: McpServer): DomainResult<McpServer>
    suspend fun findAllEnabled(): DomainResult<List<McpServer>>
    suspend fun findAll(): DomainResult<List<McpServer>>
    suspend fun findByName(name: String): DomainResult<McpServer?>
    suspend fun deleteByName(name: String): DomainResult<Unit>
    suspend fun updateEnabled(name: String, enabled: Boolean): DomainResult<Unit>
}
