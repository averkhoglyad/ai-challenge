package io.averkhogliad.ai.challenge.week6.infrastructure.tools

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GitBuiltinTool(
    private val gitPort: GitPort,
    private val projectContextProvider: ProjectContextProvider,
) : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "git_branch",
        description = "Get current git branch name",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
            put("required", buildJsonArray { })
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val ctx = when (val result = projectContextProvider.getContext()) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return ToolResult.Error(result.error.message)
        } ?: return ToolResult.Error("No active project")

        return when (val result = gitPort.getCurrentBranch(ctx.rootPath)) {
            is DomainResult.Success -> ToolResult.Success("Current branch: ${result.value}")
            is DomainResult.Failure -> ToolResult.Error(result.error.message)
        }
    }
}
