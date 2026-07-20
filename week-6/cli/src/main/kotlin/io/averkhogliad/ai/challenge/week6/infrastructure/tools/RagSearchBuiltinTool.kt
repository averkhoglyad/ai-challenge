package io.averkhogliad.ai.challenge.week6.infrastructure.tools

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.StalenessResult
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.CheckIndexStalenessUseCase
import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import kotlinx.serialization.json.*

class RagSearchBuiltinTool(
    private val ragService: RagService,
    private val checkStalenessUseCase: CheckIndexStalenessUseCase? = null,
    private val contextProvider: ProjectContextProvider? = null,
) : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "search_docs",
        description = "Search project documentation for relevant information",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query to find relevant documentation")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: query")

        return try {
            val stalenessNote = checkStaleness()
            if (stalenessNote == INDEX_MISSING_WARNING) {
                return ToolResult.Success(stalenessNote)
            }

            val results = ragService.search(query, topK = 5)

            if (results.isEmpty()) {
                val msg = buildString {
                    append("No relevant documentation found for query: $query")
                    if (stalenessNote != null) append(". $stalenessNote")
                }
                ToolResult.Success(msg)
            } else {
                val formatted = buildString {
                    appendLine("Search results for: $query")
                    if (stalenessNote != null) {
                        appendLine("⚠ $stalenessNote")
                    }
                    appendLine()
                    results.forEachIndexed { index, result ->
                        val pct = "%.2f".format(result.score)
                        val citation = when {
                            result.startLine != null && result.endLine != null ->
                                "${result.sourcePath}:${result.startLine}-${result.endLine}"

                            else -> result.sourcePath
                        }
                        appendLine("--- Result ${index + 1} (score: $pct, source: $citation) ---")
                        appendLine(result.text)
                        appendLine()
                    }
                }
                ToolResult.Success(formatted)
            }
        } catch (e: Exception) {
            ToolResult.Success("⚠ RAG unavailable: ${e.message ?: "unknown error"}")
        }
    }

    private suspend fun checkStaleness(): String? {
        val uc = checkStalenessUseCase ?: return null
        val ctx = contextProvider?.getContext()?.getOrNull() ?: return null
        return when (val result = uc.execute(ctx.projectId, ctx.rootPath)) {
            StalenessResult.NoIndex -> INDEX_MISSING_WARNING
            is StalenessResult.Stale -> "Индекс устарел: ${result.reason}"
            StalenessResult.Fresh -> null
            StalenessResult.NotApplicable -> null
        }
    }

    private companion object {
        const val INDEX_MISSING_WARNING = "⚠ Индекс отсутствует — поиск по документации недоступен"
    }
}
