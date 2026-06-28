package io.averkhogliad.ai.challenge.week3.cli.application.service

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPConnectionState

/**
 * Ссылка на MCP-сервер (пользовательский или системный) для передачи в LLM.
 *
 * В отличие от [ServerWithStatus], не требует [MCPServerConfig] —
 * подходит как для пользовательских (из БД), так и для системных (in-memory) серверов.
 */
data class McpServerRef(
    val id: ModelId,
    val name: String,
    val status: MCPConnectionState
) {
    val isConnected: Boolean get() = status.isConnected()
}
