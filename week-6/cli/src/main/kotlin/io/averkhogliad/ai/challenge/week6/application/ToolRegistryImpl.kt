package io.averkhogliad.ai.challenge.week6.application

import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolRegistry

class ToolRegistryImpl : ToolRegistry {

    private val tools = LinkedHashMap<String, Tool>()
    private val remoteToolsByServer = LinkedHashMap<String, MutableList<String>>()

    override fun register(tool: Tool) {
        val name = tool.definition.name
        require(!tools.containsKey(name)) {
            "Tool with name '$name' is already registered"
        }
        tools[name] = tool
    }

    override fun registerRemote(serverName: String, tools: List<Tool>) {
        val registeredNames = mutableListOf<String>()
        tools.forEach { tool ->
            val originalName = tool.definition.name
            var toolName = originalName

            if (this.tools.containsKey(toolName)) {
                toolName = "${serverName}__$originalName"
                System.err.println("[ToolRegistry] Tool name conflict: '$originalName' from '$serverName' renamed to '$toolName'")
            }

            this.tools[toolName] = tool
            registeredNames.add(toolName)
        }
        remoteToolsByServer.getOrPut(serverName) { mutableListOf() }.addAll(registeredNames)
    }

    override fun unregisterRemote(serverName: String) {
        remoteToolsByServer[serverName]?.forEach { toolName ->
            tools.remove(toolName)
        }
        remoteToolsByServer.remove(serverName)
    }

    override fun getTool(name: String): Tool? = tools[name]

    override fun getAllTools(): List<Tool> = tools.values.toList()

    override fun getDefinitions(): List<ToolDefinition> = tools.values.map { it.definition }
}
