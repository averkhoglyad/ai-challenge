package io.averkhogliad.ai.challenge.week6.domain.tools

interface ToolRegistry {
    fun register(tool: Tool)
    fun registerRemote(serverName: String, tools: List<Tool>)
    fun unregisterRemote(serverName: String)
    fun getTool(name: String): Tool?
    fun getAllTools(): List<Tool>
    fun getDefinitions(): List<ToolDefinition>
}
