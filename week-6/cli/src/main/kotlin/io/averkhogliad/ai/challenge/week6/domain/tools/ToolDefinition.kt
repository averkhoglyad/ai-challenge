package io.averkhogliad.ai.challenge.week6.domain.tools

import kotlinx.serialization.json.JsonObject

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val source: ToolSource = ToolSource.Builtin,
    val requiresExplicitInvocation: Boolean = false,
)
