package io.averkhogliad.ai.challenge.week6.cli.rendering

import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.cli.repl.mordant.common.TableRenderer

data class McpServerInfo(
    val name: String,
    val type: String,
    val status: String,
    val toolsCount: String,
)

class McpServerInfoRenderer(terminal: Terminal) : TableRenderer<List<McpServerInfo>>(terminal) {
    override fun headers() = listOf("Name", "Type", "Status", "Tools")
    override fun rows(data: List<McpServerInfo>) = data.map { info ->
        listOf(info.name, info.type, info.status, info.toolsCount)
    }
}
