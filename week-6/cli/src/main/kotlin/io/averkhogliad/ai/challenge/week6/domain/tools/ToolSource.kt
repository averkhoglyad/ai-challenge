package io.averkhogliad.ai.challenge.week6.domain.tools

sealed interface ToolSource {
    data object Builtin : ToolSource
    data class Remote(val serverName: String) : ToolSource
}
