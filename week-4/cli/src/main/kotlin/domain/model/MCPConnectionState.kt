package io.averkhogliad.ai.challenge.week4.cli.domain.model

import java.time.Instant

enum class MCPFailureReason {
    NOT_FOUND,
    DISABLED,
    TRANSPORT_ERROR
}

sealed class MCPConnectionState {
    data object Disconnected : MCPConnectionState()
    data object Connecting : MCPConnectionState()
    data class Connected(val connectedAt: Instant) : MCPConnectionState()
    data class Failed(
        val error: String,
        val since: Instant,
        val reason: MCPFailureReason = MCPFailureReason.TRANSPORT_ERROR
    ) : MCPConnectionState()

    fun isConnected(): Boolean = this is Connected

    fun statusDescription(): String = when (this) {
        is Disconnected -> "Disconnected"
        is Connecting -> "Connecting"
        is Connected -> "Connected since $connectedAt"
        is Failed -> "Failed: $error (since $since)"
    }
}
