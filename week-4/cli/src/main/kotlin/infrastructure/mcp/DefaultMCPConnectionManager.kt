package io.averkhogliad.ai.challenge.week4.cli.infrastructure.mcp

import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MCPClient
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MCPConnectionManager
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MCPServerRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of [MCPConnectionManager] using [ConcurrentHashMap]
 * for thread-safe client storage.
 *
 * ## Architectural role
 * - **Infrastructure Layer** — implements the [MCPConnectionManager] domain port.
 * - Wraps [MCPClientAdapter] instances per server and manages their lifecycle.
 *
 * ## System vs User servers
 * - **User servers** are managed via [MCPServerRepository] (persisted, `:mcp add/remove`).
 * - **System servers** are registered via [connectSystem] — in-memory only, not user-visible.
 *
 * ## Thread safety
 * All mutable state is guarded by [ConcurrentHashMap]. Individual [MCPClient]
 * instances are not shared across threads — each server has its own client.
 * Connection per serverId is serialized via [Mutex] to prevent race conditions.
 */
class DefaultMCPConnectionManager(
    private val serverRepository: MCPServerRepository
) : MCPConnectionManager {

    private val clients = ConcurrentHashMap<ModelId, MCPClient>()
    private val connectMutexes = ConcurrentHashMap<ModelId, Mutex>()
    private val systemServerIds = ConcurrentHashMap.newKeySet<ModelId>()

    // ──── User server connection (via repository) ────

    override suspend fun connect(serverId: ModelId): MCPConnectionState {
        val mutex = connectMutexes.computeIfAbsent(serverId) { Mutex() }
        return mutex.withLock {
            val existing = clients[serverId]
            if (existing != null && existing.isConnected()) {
                return@withLock existing.getStatus()
            }

            val config = serverRepository.findById(serverId)
                ?: return@withLock MCPConnectionState.Failed(
                    error = "Server not found: $serverId",
                    since = Instant.now(),
                    reason = MCPFailureReason.NOT_FOUND
                )

            if (!config.enabled)
                return@withLock MCPConnectionState.Failed(
                    error = "Server is disabled: ${config.name}",
                    since = Instant.now(),
                    reason = MCPFailureReason.DISABLED
                )

            val client = MCPClientAdapter()
            val state = client.connect(config)
            if (state is MCPConnectionState.Connected) {
                clients[serverId] = client
            }
            return@withLock state
        }
    }

    // ──── System server connection (in-memory, bypasses repository) ────

    override suspend fun connectSystem(
        id: ModelId,
        name: String,
        transport: MCPTransport
    ): MCPConnectionState {
        val mutex = connectMutexes.computeIfAbsent(id) { Mutex() }
        return mutex.withLock {
            val existing = clients[id]
            if (existing != null && existing.isConnected()) {
                return@withLock existing.getStatus()
            }

            val config = MCPServerConfig(
                id = id,
                name = name,
                transport = transport,
                enabled = true,
                createdAt = Instant.now()
            )

            val client = MCPClientAdapter()
            val state = client.connect(config)
            if (state is MCPConnectionState.Connected) {
                clients[id] = client
                systemServerIds.add(id)
            }
            return@withLock state
        }
    }

    override fun getSystemServerIds(): Set<ModelId> = systemServerIds.toSet()

    // ──── Common operations ────

    override suspend fun disconnect(serverId: ModelId) {
        clients[serverId]?.disconnect()
        clients.remove(serverId)
        systemServerIds.remove(serverId)
    }

    override fun getStatus(serverId: ModelId): MCPConnectionState {
        return clients[serverId]?.getStatus() ?: MCPConnectionState.Disconnected
    }

    override suspend fun getTools(serverId: ModelId): List<MCPTool> {
        val client = clients[serverId] ?: return emptyList()
        return if (client.isConnected()) client.listTools() else emptyList()
    }

    override suspend fun callTool(
        serverId: ModelId,
        name: String,
        arguments: Map<String, Any?>
    ): io.modelcontextprotocol.kotlin.sdk.types.CallToolResult {
        val client = clients[serverId]
            ?: throw IllegalStateException("MCP client for server $serverId not found")
        return client.callTool(name, arguments)
    }

    override suspend fun getPrompts(serverId: ModelId): List<McpPrompt> {
        val client = clients[serverId] ?: return emptyList()
        return if (client.isConnected()) client.listPrompts() else emptyList()
    }

    override suspend fun getPrompt(
        serverId: ModelId,
        name: String,
        arguments: Map<String, String>
    ): List<McpPromptMessage> {
        val client = clients[serverId] ?: return emptyList()
        return client.getPrompt(name, arguments)
    }

    override fun isConnected(serverId: ModelId): Boolean {
        return clients[serverId]?.isConnected() == true
    }
}
