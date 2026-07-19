package io.averkhogliad.ai.challenge.week6.domain.tools

import kotlinx.serialization.json.JsonObject

interface Tool {
    val definition: ToolDefinition
    suspend fun execute(arguments: JsonObject): ToolResult
}
