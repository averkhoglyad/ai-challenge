package io.averkhogliad.ai.challenge.week6.application.mcp

import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import kotlinx.serialization.json.JsonObject

class RemoteToolAdapter(
    private val callExecutor: suspend (JsonObject) -> ToolResult,
    toolDef: ToolDefinition,
) : Tool {
    override val definition: ToolDefinition = toolDef

    override suspend fun execute(arguments: JsonObject): ToolResult {
        return try {
            callExecutor(arguments)
        } catch (e: Exception) {
            ToolResult.Error("Tool execution failed: ${e.message}")
        }
    }
}
