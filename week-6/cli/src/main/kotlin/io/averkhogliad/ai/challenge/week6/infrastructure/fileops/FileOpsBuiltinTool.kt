package io.averkhogliad.ai.challenge.week6.infrastructure.fileops

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileFilter
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.SearchQuery
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort
import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import kotlinx.serialization.json.*

class FileOpsBuiltinTool(
    private val fileOpsPort: FileOpsPort,
    private val projectContextProvider: ProjectContextProvider,
) {

    fun createTools(): List<Tool> = listOf(
        createReadFileTool(),
        createWriteFileTool(),
        createSearchCodeTool(),
        createListFilesTool(),
        createFileInfoTool(),
    )

    private fun createReadFileTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "read_file",
            description = "Read the content of a file in the project",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val pathStr = arguments["path"]?.jsonPrimitive?.content
                ?: return ToolResult.Error("Missing required parameter: path")

            val ctx = requireContext() ?: return ToolResult.Error("No active project. Use /open first.")
            val relPath = resolvePath(pathStr, ctx.rootPath) ?: return ToolResult.Error("Invalid path: $pathStr")

            return when (val result = fileOpsPort.read(relPath)) {
                is DomainResult.Success -> {
                    val fc = result.value
                    val output = buildString {
                        if (fc.truncated) {
                            appendLine("⚠ File truncated to 64KB (actual size: ${fc.sizeBytes} bytes)")
                            appendLine()
                        }
                        append(fc.content)
                    }
                    ToolResult.Success(output)
                }

                is DomainResult.Failure -> ToolResult.Error(result.error.message)
            }
        }
    }

    private fun createWriteFileTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "write_file",
            description = "Write content to a file. Requires explicit invocation via /refactor.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "New content of the file")
                    }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("path"))
                    add(JsonPrimitive("content"))
                })
            },
            requiresExplicitInvocation = true,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val pathStr = arguments["path"]?.jsonPrimitive?.content
                ?: return ToolResult.Error("Missing required parameter: path")
            val content = arguments["content"]?.jsonPrimitive?.content
                ?: return ToolResult.Error("Missing required parameter: content")

            val ctx = requireContext() ?: return ToolResult.Error("No active project. Use /open first.")
            val relPath = resolvePath(pathStr, ctx.rootPath) ?: return ToolResult.Error("Invalid path: $pathStr")

            return ToolResult.PendingConfirm(
                "Write to $pathStr requires confirmation via /refactor."
            )
        }
    }

    private fun createSearchCodeTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "search_code",
            description = "Search for text in project files (substring, case-insensitive by default)",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Text to search for (substring match)")
                    }
                    putJsonObject("ext") {
                        put("type", "string")
                        put("description", "Filter by file extension (e.g. '.kt')")
                    }
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Subdirectory to search in")
                    }
                    putJsonObject("caseSensitive") {
                        put("type", "boolean")
                        put("description", "Enable case-sensitive search")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("query")) })
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val queryText = arguments["query"]?.jsonPrimitive?.content
                ?: return ToolResult.Error("Missing required parameter: query")

            val ctx = requireContext() ?: return ToolResult.Error("No active project. Use /open first.")

            val ext = arguments["ext"]?.jsonPrimitive?.contentOrNull
            val caseSensitive = arguments["caseSensitive"]?.jsonPrimitive?.booleanOrNull ?: false
            val subDir = arguments["path"]?.jsonPrimitive?.contentOrNull

            val inDir = if (subDir != null) {
                resolvePath(subDir, ctx.rootPath) ?: return ToolResult.Error("Invalid path: $subDir")
            } else null

            val searchQuery = SearchQuery(
                query = queryText,
                ignoreCase = !caseSensitive,
                extension = ext,
                inDirectory = inDir,
            )

            return when (val result = fileOpsPort.search(searchQuery)) {
                is DomainResult.Success -> {
                    val hits = result.value
                    if (hits.isEmpty()) {
                        ToolResult.Success("No results found for: $queryText")
                    } else {
                        val formatted = buildString {
                            appendLine("Results for: $queryText (${hits.size} hits)")
                            hits.forEach { hit ->
                                appendLine("${hit.path}:${hit.line}: ${hit.snippet.take(120)}")
                            }
                        }
                        ToolResult.Success(formatted)
                    }
                }

                is DomainResult.Failure -> ToolResult.Error(result.error.message)
            }
        }
    }

    private fun createListFilesTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "list_files",
            description = "List files in a directory",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("dir") {
                        put("type", "string")
                        put("description", "Directory to list (relative path, default: root)")
                    }
                }
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val ctx = requireContext() ?: return ToolResult.Error("No active project. Use /open first.")
            val dirStr = arguments["dir"]?.jsonPrimitive?.contentOrNull ?: "."
            val relDir = resolvePath(dirStr, ctx.rootPath) ?: return ToolResult.Error("Invalid path: $dirStr")

            return when (val result = fileOpsPort.list(relDir, FileFilter())) {
                is DomainResult.Success -> {
                    val files = result.value
                    if (files.isEmpty()) {
                        ToolResult.Success("Directory is empty: $dirStr")
                    } else {
                        val formatted = buildString {
                            appendLine("Files in $dirStr (${files.size} entries):")
                            files.forEach { f ->
                                val type = if (f.isDirectory) "[DIR] " else "[FILE]"
                                val size = if (!f.isDirectory) " ${formatSize(f.sizeBytes)}" else ""
                                val binary = if (f.isBinary) " [BIN]" else ""
                                appendLine("  $type${f.path}$size$binary")
                            }
                        }
                        ToolResult.Success(formatted)
                    }
                }

                is DomainResult.Failure -> ToolResult.Error(result.error.message)
            }
        }
    }

    private fun createFileInfoTool(): Tool = object : Tool {
        override val definition: ToolDefinition = ToolDefinition(
            name = "file_info",
            description = "Get metadata about a file (size, type, modification time)",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            },
            requiresExplicitInvocation = false,
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val pathStr = arguments["path"]?.jsonPrimitive?.content
                ?: return ToolResult.Error("Missing required parameter: path")

            val ctx = requireContext() ?: return ToolResult.Error("No active project. Use /open first.")
            val relPath = resolvePath(pathStr, ctx.rootPath) ?: return ToolResult.Error("Invalid path: $pathStr")

            return when (val result = fileOpsPort.info(relPath)) {
                is DomainResult.Success -> {
                    val meta = result.value
                    ToolResult.Success(
                        buildString {
                            appendLine("File: ${meta.path}")
                            appendLine("Size: ${formatSize(meta.sizeBytes)}")
                            appendLine("Type: ${if (meta.isDirectory) "Directory" else "File"}")
                            appendLine("Modified: ${meta.lastModified}")
                            appendLine("Binary: ${if (meta.isBinary) "Yes" else "No"}")
                        }
                    )
                }

                is DomainResult.Failure -> ToolResult.Error(result.error.message)
            }
        }
    }

    private suspend fun requireContext() = when (val r = projectContextProvider.getContext()) {
        is DomainResult.Success -> r.value
        is DomainResult.Failure -> null
    }

    private fun resolvePath(pathStr: String, rootPath: java.nio.file.Path) =
        when (val r = RelativePath.from(pathStr, rootPath)) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> null
        }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
