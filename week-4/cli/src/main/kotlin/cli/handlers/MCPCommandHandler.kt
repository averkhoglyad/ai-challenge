package io.averkhogliad.ai.challenge.week4.cli.cli.handlers

import io.averkhogliad.ai.challenge.week4.cli.application.service.MCPOperationError
import io.averkhogliad.ai.challenge.week4.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.MCPRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPTransport

/**
 * Handler для обработки команд управления MCP-серверами.
 *
 * Поддерживает многошаговые потоки (multi-step input) для добавления серверов,
 * аналогично [ProfileCommandHandler].
 */
class MCPCommandHandler(
    private val mcpService: MCPService,
    private val renderer: MCPRenderer,
    private val readLine: () -> String? = { readlnOrNull() }
) {
    // ═══════════════════════════════════════════════════════════════
    // McpAddServerRequest
    // ═══════════════════════════════════════════════════════════════

    suspend fun handleMcpAddServerRequest(state: CliState): CliState {
        renderer.renderAddServerPrompt()
        val transportChoice = readLine()?.trim()?.lowercase()

        return when (transportChoice) {
            "stdio" -> handleStdioPrompt(state)
            "http" -> handleHttpPrompt(state)
            else -> {
                renderer.renderError("Неверный тип транспорта. Ожидается 'stdio' или 'http'")
                state
            }
        }
    }

    private suspend fun handleStdioPrompt(state: CliState): CliState {
        renderer.renderStdioPrompt()
        val line = readLine()?.trim() ?: ""
        if (line.isBlank()) {
            renderer.renderError("Имя сервера и команда обязательны")
            return state
        }
        val parts = line.split(" ", limit = 2)
        if (parts.size < 2) {
            renderer.renderError("Укажите имя сервера и команду через пробел")
            return state
        }
        val name = parts[0]
        val cmdAndArgs = parts[1].split(" ")
        val command = cmdAndArgs[0]
        val args = cmdAndArgs.drop(1)
        val transport = MCPTransport.Stdio(command, args)
        return handleMcpAddServer(Command.McpAddServer(name, transport), state)
    }

    private suspend fun handleHttpPrompt(state: CliState): CliState {
        renderer.renderHttpPrompt()
        val line = readLine()?.trim() ?: ""
        if (line.isBlank()) {
            renderer.renderError("Имя сервера и URL обязательны")
            return state
        }
        val parts = line.split(" ", limit = 2)
        if (parts.size < 2) {
            renderer.renderError("Укажите имя сервера и URL через пробел")
            return state
        }
        val name = parts[0]
        val url = parts[1]
        val transport = MCPTransport.StreamableHttp(url)
        return handleMcpAddServer(Command.McpAddServer(name, transport), state)
    }

    // ═══════════════════════════════════════════════════════════════
    // McpAddServer — прямое добавление (из парсера)
    // ═══════════════════════════════════════════════════════════════

    suspend fun handleMcpAddServer(command: Command.McpAddServer, state: CliState): CliState {
        val result = mcpService.addServer(command.name, command.transport)
        result.onSuccess { config -> renderer.renderServerAdded(config) }
        result.onFailure { error ->
            renderer.renderError((error as? MCPOperationError)?.message ?: error.message ?: "Unknown error")
        }
        return state
    }

    // ═══════════════════════════════════════════════════════════════
    // McpListServers
    // ═══════════════════════════════════════════════════════════════

    suspend fun handleMcpListServers(state: CliState): CliState {
        try {
            val servers = mcpService.listServers()
            renderer.renderServerList(servers)
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    // ═══════════════════════════════════════════════════════════════
    // McpRemoveServerRequest — запрос имени для удаления
    // ═══════════════════════════════════════════════════════════════

    suspend fun handleMcpRemoveServerRequest(state: CliState): CliState {
        renderer.renderError("Укажите имя сервера для удаления: :mcp-remove <name>")
        return state
    }

    // ═══════════════════════════════════════════════════════════════
    // McpRemoveServer — удаление с отключением
    // ═══════════════════════════════════════════════════════════════

    suspend fun handleMcpRemoveServer(command: Command.McpRemoveServer, state: CliState): CliState {
        val servers = mcpService.listServers()
        val server = servers.find { it.config.name == command.serverName }
        if (server == null) {
            renderer.renderError("Сервер '${command.serverName}' не найден")
            return state
        }
        val result = mcpService.removeServer(server.config.id)
        result.onSuccess { name -> renderer.renderServerRemoved(name) }
        result.onFailure { error ->
            renderer.renderError((error as? MCPOperationError)?.message ?: error.message ?: "Unknown error")
        }
        return state
    }

    // ═══════════════════════════════════════════════════════════════
    // McpConnectServer
    // ═══════════════════════════════════════════════════════════════

    suspend fun handleMcpConnectServer(command: Command.McpConnectServer, state: CliState): CliState {
        val servers = mcpService.listServers()
        val server = servers.find { it.config.name == command.serverName }
        if (server == null) {
            renderer.renderError("Сервер '${command.serverName}' не найден")
            return state
        }
        val result = mcpService.connect(server.config.id)
        result.onSuccess { connectionState ->
            when (connectionState) {
                is io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPConnectionState.Connected ->
                    renderer.renderConnectionSuccess(server.config)

                is io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPConnectionState.Connecting ->
                    renderer.renderConnecting(server.config.name)

                is io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPConnectionState.Disconnected ->
                    renderer.renderDisconnected(server.config.name)

                else -> {} // Failed handled in onFailure
            }
        }
        result.onFailure { error ->
            renderer.renderError((error as? MCPOperationError)?.message ?: error.message ?: "Unknown error")
        }
        return state
    }

    // ═══════════════════════════════════════════════════════════════
    // McpDisconnectServer
    // ═══════════════════════════════════════════════════════════════

    suspend fun handleMcpDisconnectServer(command: Command.McpDisconnectServer, state: CliState): CliState {
        try {
            val servers = mcpService.listServers()
            val server = servers.find { it.config.name == command.serverName }
            if (server == null) {
                renderer.renderError("Сервер '${command.serverName}' не найден")
                return state
            }
            mcpService.disconnect(server.config.id)
            renderer.renderDisconnected(server.config.name)
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    // ═══════════════════════════════════════════════════════════════
    // McpToolsServer
    // ═══════════════════════════════════════════════════════════════

    suspend fun handleMcpToolsServer(command: Command.McpToolsServer, state: CliState): CliState {
        val servers = mcpService.listServers()
        val server = servers.find { it.config.name == command.serverName }
        if (server == null) {
            renderer.renderError("Сервер '${command.serverName}' не найден")
            return state
        }
        val result = mcpService.getTools(server.config.id)
        result.onSuccess { tools -> renderer.renderToolsList(tools) }
        result.onFailure { error ->
            renderer.renderError((error as? MCPOperationError)?.message ?: error.message ?: "Unknown error")
        }
        return state
    }
}
