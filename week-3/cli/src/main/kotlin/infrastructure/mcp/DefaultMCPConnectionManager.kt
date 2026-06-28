package io.averkhogliad.ai.challenge.week3.cli.infrastructure.mcp

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPFailureReason
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MCPClient
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MCPConnectionManager
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MCPServerRepository
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
 * ## Thread safety
 * All mutable state is guarded by [ConcurrentHashMap]. Individual [MCPClient]
 * instances are not shared across threads — each server has its own client.
 * Connection per serverId is serialized via [Mutex] to prevent race conditions.
 *
 * ## Error handling
 * - Connection failures are propagated as [MCPConnectionState.Failed] from the
 *   underlying [MCPClient].
 * - Operations on unknown server IDs return safe defaults (disconnected state,
 *   empty tool list).
 */
class DefaultMCPConnectionManager(
    private val serverRepository: MCPServerRepository
) : MCPConnectionManager {

    private val clients = ConcurrentHashMap<ModelId, MCPClient>()
    private val connectMutexes = ConcurrentHashMap<ModelId, Mutex>()

    override suspend fun connect(serverId: ModelId): MCPConnectionState {
        val mutex = connectMutexes.computeIfAbsent(serverId) { Mutex() }
        return mutex.withLock {
            // Check existing client first
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

    override suspend fun disconnect(serverId: ModelId) {
        clients[serverId]?.disconnect()
        clients.remove(serverId)
    }

    override fun getStatus(serverId: ModelId): MCPConnectionState {
        return clients[serverId]?.getStatus() ?: MCPConnectionState.Disconnected
    }

    override suspend fun getTools(serverId: ModelId): List<MCPTool> {
        val client = clients[serverId] ?: return emptyList()
        return if (client.isConnected()) client.listTools() else emptyList()
    }

    override fun isConnected(serverId: ModelId): Boolean {
        return clients[serverId]?.isConnected() == true
    }
}
