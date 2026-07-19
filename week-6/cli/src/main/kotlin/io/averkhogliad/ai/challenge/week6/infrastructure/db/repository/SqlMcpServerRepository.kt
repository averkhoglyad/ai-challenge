package io.averkhogliad.ai.challenge.week6.infrastructure.db.repository

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.McpServer
import io.averkhogliad.ai.challenge.week6.domain.model.McpServerType
import io.averkhogliad.ai.challenge.week6.domain.port.McpServerRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.McpServersTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

class SqlMcpServerRepository : McpServerRepository {

    override suspend fun save(server: McpServer): DomainResult<McpServer> = transaction {
        try {
            val existing = McpServersTable.selectAll()
                .where { McpServersTable.name eq server.name }
                .singleOrNull()
            if (existing != null) {
                McpServersTable.update({ McpServersTable.name eq server.name }) {
                    it[serverType] = server.serverType.value
                    it[baseUrl] = server.baseUrl
                    it[transportConfig] = server.transportConfig
                    it[enabled] = if (server.enabled) 1 else 0
                }
            } else {
                McpServersTable.insert {
                    it[id] = server.id
                    it[name] = server.name
                    it[serverType] = server.serverType.value
                    it[baseUrl] = server.baseUrl
                    it[transportConfig] = server.transportConfig
                    it[enabled] = if (server.enabled) 1 else 0
                    it[createdAt] = server.createdAt.toEpochMilli()
                }
            }
            DomainResult.Success(server)
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findAllEnabled(): DomainResult<List<McpServer>> = transaction {
        try {
            val rows = McpServersTable.selectAll()
                .where { McpServersTable.enabled eq 1 }
                .toList()
            DomainResult.Success(rows.map(::toMcpServer))
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findAll(): DomainResult<List<McpServer>> = transaction {
        try {
            val rows = McpServersTable.selectAll().toList()
            DomainResult.Success(rows.map(::toMcpServer))
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun findByName(name: String): DomainResult<McpServer?> = transaction {
        try {
            val row = McpServersTable.selectAll()
                .where { McpServersTable.name eq name }
                .singleOrNull()
            DomainResult.Success(row?.let(::toMcpServer))
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun deleteByName(name: String): DomainResult<Unit> = transaction {
        try {
            McpServersTable.deleteWhere { McpServersTable.name eq name }
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun updateEnabled(name: String, enabled: Boolean): DomainResult<Unit> = transaction {
        try {
            val updated = McpServersTable.update({ McpServersTable.name eq name }) {
                it[McpServersTable.enabled] = if (enabled) 1 else 0
            }
            if (updated == 0) {
                DomainResult.Failure(DomainError.mcpServerNotFound(name))
            } else {
                DomainResult.Success(Unit)
            }
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    private fun toMcpServer(row: ResultRow): McpServer = McpServer(
        id = row[McpServersTable.id],
        name = row[McpServersTable.name],
        serverType = parseServerType(row[McpServersTable.serverType]),
        baseUrl = row[McpServersTable.baseUrl],
        transportConfig = row[McpServersTable.transportConfig],
        enabled = row[McpServersTable.enabled] == 1,
        createdAt = Instant.ofEpochMilli(row[McpServersTable.createdAt]),
    )

    private fun parseServerType(value: String): McpServerType = when (value) {
        "http_sse" -> McpServerType.HTTP_SSE
        "stdio" -> McpServerType.STDIO
        "http_streamable" -> McpServerType.HTTP_STREAMABLE
        else -> McpServerType.HTTP_SSE
    }
}
