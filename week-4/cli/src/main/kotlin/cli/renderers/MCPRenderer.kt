package io.averkhogliad.ai.challenge.week4.cli.cli.renderers

import io.averkhogliad.ai.challenge.week4.cli.application.service.ServerWithStatus
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.CYAN
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.GREEN
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.RED
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.RESET
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.ConsoleColors.YELLOW
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPServerConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPTool

/**
 * Специализированный рендерер для MCP-серверов.
 *
 * Выделен из [ConsoleCliRenderer] для соблюдения Single Responsibility Principle.
 * Не зависит от внешних сервисов — легко тестируется (перехват System.out).
 */
class MCPRenderer {

    private val hline: String get() = "\u2500".repeat(80)

    fun renderServerList(servers: List<ServerWithStatus>) {
        println()
        if (servers.isEmpty()) {
            println("${YELLOW}\uD83D\uDD0C MCP-серверы не найдены${RESET}")
        } else {
            println("${CYAN}\uD83D\uDD0C MCP-серверы:${RESET}")
            println("${CYAN}${hline}${RESET}")
            println("  ${CYAN}Имя                                     ID                              Транспорт      Статус${RESET}")
            println("${CYAN}${hline}${RESET}")
            servers.forEach { server ->
                val name = server.config.name.padEnd(40)
                val id = server.config.id.value.take(8)
                val transport = transportLabel(server.config)
                val statusStr = statusText(server.status)
                val color = statusColor(server.status)
                println("  $name $id...  ${transport.padEnd(14)}  ${color}$statusStr${RESET}")
            }
            println("${CYAN}${hline}${RESET}")
        }
        println()
    }

    fun renderServerAdded(config: MCPServerConfig) {
        println()
        println("${GREEN}\u2705 MCP-сервер \"${config.name}\" добавлен (ID: ${config.id.value.take(8)}...)${RESET}")
        println()
    }

    fun renderServerRemoved(name: String) {
        println()
        println("${GREEN}\u2705 MCP-сервер \"$name\" удалён${RESET}")
        println()
    }

    fun renderConnectionSuccess(config: MCPServerConfig) {
        println()
        println("${GREEN}\u2705 Подключено к MCP-серверу \"${config.name}\"${RESET}")
        println()
    }

    fun renderConnecting(name: String) {
        println()
        println("${YELLOW}\u23F3 Подключение к MCP-серверу \"$name\"...${RESET}")
        println()
    }

    fun renderConnectionFailed(error: String) {
        println()
        println("${RED}\u274C [ОШИБКА] $error${RESET}")
        println()
    }

    fun renderDisconnected(name: String) {
        println()
        println("${GREEN}\u2705 Отключено от MCP-сервера \"$name\"${RESET}")
        println()
    }

    fun renderToolsList(tools: List<MCPTool>) {
        println()
        if (tools.isEmpty()) {
            println("${YELLOW}\uD83D\uDD27 Инструменты не найдены${RESET}")
        } else {
            println("${CYAN}\uD83D\uDD27 Инструменты MCP-сервера:${RESET}")
            println("${CYAN}${hline}${RESET}")
            println("  ${CYAN}Имя                                     Описание${RESET}")
            println("${CYAN}${hline}${RESET}")
            tools.forEach { tool ->
                val name = tool.name.take(40).padEnd(40)
                val desc = tool.description?.take(40) ?: "(нет описания)"
                println("  $name $desc")
            }
            println("${CYAN}${hline}${RESET}")
        }
        println()
    }

    fun renderError(message: String) {
        println()
        println("${RED}\u274C [ОШИБКА] $message${RESET}")
        println()
    }

    fun renderAddServerPrompt() {
        println()
        println("${CYAN}\uD83D\uDD0C Выберите тип транспорта (stdio / http):${RESET}")
        print("${CYAN}> ${RESET}")
    }

    fun renderStdioPrompt() {
        println()
        println("${CYAN}\uD83D\uDCDD Введите: <имя> <команда> [аргументы...]${RESET}")
        println("${CYAN}   Пример: my-server npx @modelcontextprotocol/server-filesystem /path${RESET}")
        print("${CYAN}> ${RESET}")
    }

    fun renderHttpPrompt() {
        println()
        println("${CYAN}\uD83D\uDCDD Введите: <имя> <url>${RESET}")
        println("${CYAN}   Пример: my-server https://example.com/mcp${RESET}")
        print("${CYAN}> ${RESET}")
    }

    // ──── Private helpers ────

    private fun transportLabel(config: MCPServerConfig): String = when (config.transport) {
        is io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPTransport.Stdio -> "Stdio"
        is io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPTransport.StreamableHttp -> "HTTP"
    }

    private fun statusColor(state: MCPConnectionState): String = when (state) {
        is MCPConnectionState.Connected -> GREEN
        is MCPConnectionState.Connecting -> YELLOW
        is MCPConnectionState.Disconnected -> RESET
        is MCPConnectionState.Failed -> RED
    }

    private fun statusText(state: MCPConnectionState): String = when (state) {
        is MCPConnectionState.Connected -> "Connected"
        is MCPConnectionState.Connecting -> "Connecting"
        is MCPConnectionState.Disconnected -> "Disconnected"
        is MCPConnectionState.Failed -> "Failed"
    }
}
