package io.averkhogliad.ai.challenge.week3.cli.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import java.time.Instant
import java.util.*

data class MCPServerConfig(
    val id: ModelId,
    val name: String,
    val transport: MCPTransport,
    val enabled: Boolean = true,
    val createdAt: Instant
) {
    init {
        require(name.length in 1..50) { "Name must be 1-50 characters" }
        require(name.matches(Regex("^[a-zA-Z0-9\\-]+$"))) { "Name must match ^[a-zA-Z0-9\\-]+$" }
    }

    companion object {
        fun create(name: String, transport: MCPTransport): MCPServerConfig {
            return MCPServerConfig(
                id = ModelId(UUID.randomUUID().toString()),
                name = name,
                transport = transport,
                enabled = true,
                createdAt = Instant.now()
            )
        }
    }
}
