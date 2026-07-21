package io.averkhogliad.ai.challenge.week6.infrastructure.tools

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import kotlinx.serialization.json.*

class GitBuiltinTool(
    private val gitPort: GitPort,
    private val projectContextProvider: ProjectContextProvider,
) {

    fun createTools(): List<Tool> = listOf(
        createBranchTool(),
        createStatusTool(),
        createDiffTool(),
        createLogTool(),
        createCurrentCommitTool(),
    )

    // ── git_branch ────────────────────────────────────────────────

    private fun createBranchTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "git_branch",
            description = "Get current git branch name",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
                put("required", buildJsonArray { })
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val ctx = when (val r = projectContextProvider.getContext()) {
                is DomainResult.Success -> r.value ?: return noProjectError()
                is DomainResult.Failure -> return ToolResult.Error(r.error.message)
            }
            return when (val r = gitPort.getCurrentBranch(ctx.rootPath)) {
                is DomainResult.Success -> ToolResult.Success("Current branch: ${r.value}")
                is DomainResult.Failure -> ToolResult.Error(r.error.message)
            }
        }
    }

    // ── git_status ────────────────────────────────────────────────

    private fun createStatusTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "git_status",
            description = "Check if working tree has uncommitted changes (dirty vs clean)",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
                put("required", buildJsonArray { })
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val ctx = when (val r = projectContextProvider.getContext()) {
                is DomainResult.Success -> r.value ?: return noProjectError()
                is DomainResult.Failure -> return ToolResult.Error(r.error.message)
            }
            return when (val r = gitPort.checkGitStatus(ctx.rootPath)) {
                is DomainResult.Success -> ToolResult.Success(
                    if (r.value) "Working tree: DIRTY (uncommitted changes)" else "Working tree: CLEAN"
                )

                is DomainResult.Failure -> ToolResult.Error(r.error.message)
            }
        }
    }

    // ── git_diff ──────────────────────────────────────────────────

    private fun createDiffTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "git_diff",
            description = "Get git diff between two commits/branches. " +
                    "Shows WHAT CHANGED between versions. " +
                    "Use read_file to see current file contents, not git_diff.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("base", buildJsonObject {
                        put("type", "string")
                        put("description", "Base commit/branch/tag (e.g. 'HEAD~1', 'main', 'v1.0')")
                    })
                    put("head", buildJsonObject {
                        put("type", "string")
                        put("description", "Target commit/branch (default: HEAD)")
                    })
                    put("maxLines", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max output lines (default: 200, max: 500)")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("base")) })
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val ctx = when (val r = projectContextProvider.getContext()) {
                is DomainResult.Success -> r.value ?: return noProjectError()
                is DomainResult.Failure -> return ToolResult.Error(r.error.message)
            }
            val base = arguments["base"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult.Error("Missing required parameter: base")
            val head = arguments["head"]?.jsonPrimitive?.contentOrNull ?: "HEAD"
            val maxLines = arguments["maxLines"]?.jsonPrimitive?.intOrNull
                ?.coerceIn(1, MAX_DIFF_LINES) ?: DEFAULT_DIFF_LINES

            return when (val r = gitPort.getDiffBetweenCommits(ctx.rootPath, base, head)) {
                is DomainResult.Success -> {
                    val truncated = truncateLines(r.value, maxLines)
                    ToolResult.Success(truncated)
                }

                is DomainResult.Failure -> ToolResult.Error(r.error.message)
            }
        }
    }

    // ── git_log ───────────────────────────────────────────────────

    private fun createLogTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "git_log",
            description = "Get git commit history for a range (e.g. 'main..HEAD', 'v1.0..v2.0'). " +
                    "Returns subject line only (not full commit body). " +
                    "Includes hash, author, date, message, changed files.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("range", buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            "Revision range: 'base..head' or a single ref (default: 'HEAD' shows last commits)"
                        )
                    })
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max commits (default: 50, max: 500)")
                    })
                    put("maxLines", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max output lines (default: 200, max: 500)")
                    })
                })
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val ctx = when (val r = projectContextProvider.getContext()) {
                is DomainResult.Success -> r.value ?: return noProjectError()
                is DomainResult.Failure -> return ToolResult.Error(r.error.message)
            }
            val range = arguments["range"]?.jsonPrimitive?.contentOrNull ?: "HEAD"
            val limit = arguments["limit"]?.jsonPrimitive?.intOrNull
                ?.coerceIn(1, MAX_COMMITS) ?: DEFAULT_COMMITS
            val maxLines = arguments["maxLines"]?.jsonPrimitive?.intOrNull
                ?.coerceIn(1, MAX_LOG_LINES) ?: DEFAULT_LOG_LINES

            val (base, head) = parseRange(range)
            return when (val r = gitPort.getCommitsBetween(ctx.rootPath, base, head, limit)) {
                is DomainResult.Success -> {
                    val truncated = truncateLines(formatGitLog(r.value), maxLines)
                    ToolResult.Success(truncated)
                }

                is DomainResult.Failure -> ToolResult.Error(r.error.message)
            }
        }
    }

    // ── git_current_commit ────────────────────────────────────────

    private fun createCurrentCommitTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "git_current_commit",
            description = "Get current commit hash (HEAD)",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
                put("required", buildJsonArray { })
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val ctx = when (val r = projectContextProvider.getContext()) {
                is DomainResult.Success -> r.value ?: return noProjectError()
                is DomainResult.Failure -> return ToolResult.Error(r.error.message)
            }
            return when (val r = gitPort.getCurrentCommit(ctx.rootPath)) {
                is DomainResult.Success -> ToolResult.Success("HEAD: ${r.value}")
                is DomainResult.Failure -> ToolResult.Error(r.error.message)
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────

    private fun noProjectError() = ToolResult.Error("No active project. Use /open first.")

    private fun truncateLines(text: String, maxLines: Int): String {
        val lines = text.split('\n')
        if (lines.size <= maxLines) return text
        val prefix = lines.take(maxLines).joinToString("\n")
        return "$prefix\n\n... (truncated ${lines.size - maxLines} lines, total ${lines.size})"
    }

    private fun formatGitLog(rawOutput: String): String {
        // rawOutput uses \u001e record separator and \u001f field separator
        val commits = rawOutput.split("\u001e").filter(String::isNotBlank)
        if (commits.isEmpty()) return "No commits found."
        return commits.joinToString("\n---\n") { record ->
            val fields = record.trim().split("\u001f", limit = 6)
            if (fields.size < 5) record.trim()
            else {
                val (hash, shortHash, author, date, message) = fields
                buildString {
                    appendLine("commit $hash ($shortHash)")
                    appendLine("Author: $author")
                    appendLine("Date: $date")
                    appendLine()
                    val firstLine = message.lineSequence().firstOrNull().orEmpty()
                    appendLine(firstLine)
                    if (fields.size >= 6) {
                        val files = fields[5].trim()
                        if (files.isNotEmpty()) {
                            appendLine("Files:")
                            files.lineSequence().forEach { appendLine("  $it") }
                        }
                    }
                }
            }
        }
    }

    private fun parseRange(range: String): Pair<String?, String> {
        if (".." !in range) return null to range
        val parts = range.split("..", limit = 2)
        return parts[0].ifBlank { null } to parts[1]
    }

    private companion object {
        const val DEFAULT_DIFF_LINES = 200
        const val MAX_DIFF_LINES = 500
        const val DEFAULT_COMMITS = 50
        const val MAX_COMMITS = 500
        const val DEFAULT_LOG_LINES = 200
        const val MAX_LOG_LINES = 500
    }
}
