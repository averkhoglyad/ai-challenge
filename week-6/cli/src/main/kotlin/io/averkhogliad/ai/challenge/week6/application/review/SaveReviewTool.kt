package io.averkhogliad.ai.challenge.week6.application.review

import io.averkhogliad.ai.challenge.week6.domain.review.FindingCategory
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewFinding
import io.averkhogliad.ai.challenge.week6.domain.review.Severity
import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive

class SaveReviewTool : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "save_review",
        description = "Save code review findings. Call this after completing the review analysis.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("summary") {
                    put("type", "string")
                    put("description", "Overall summary of the code review")
                }
                putJsonObject("findings") {
                    put("type", "array")
                    put("description", "List of review findings")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("category") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Category: BUG, ARCHITECTURE, PERFORMANCE, SECURITY, BEST_PRACTICE, READABILITY, MAINTAINABILITY, OTHER"
                                )
                            }
                            putJsonObject("severity") {
                                put("type", "string")
                                put("description", "Severity: CRITICAL, WARNING, INFO")
                            }
                            putJsonObject("file") {
                                put("type", "string")
                                put("description", "File path where the issue was found (optional)")
                            }
                            putJsonObject("line") {
                                put("type", "integer")
                                put("description", "Line number (optional)")
                            }
                            putJsonObject("description") {
                                put("type", "string")
                                put("description", "Description of the issue")
                            }
                            putJsonObject("recommendation") {
                                put("type", "string")
                                put("description", "Recommendation for fixing the issue (optional)")
                            }
                        }
                        put("required", buildJsonArray {
                            add(JsonPrimitive("category"))
                            add(JsonPrimitive("severity"))
                            add(JsonPrimitive("description"))
                        })
                    }
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("findings"))
            })
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val summary = arguments["summary"]?.jsonPrimitive?.content
        val findingsArray = arguments["findings"]?.jsonArray

        if (findingsArray == null) {
            return ToolResult.Error("Missing required parameter: findings")
        }

        val findings = findingsArray.mapNotNull { element ->
            val obj = element.jsonObject
            val categoryStr = obj["category"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val severityStr = obj["severity"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val description = obj["description"]?.jsonPrimitive?.content ?: return@mapNotNull null

            ReviewFinding(
                category = JsonReviewParser.parseCategory(categoryStr),
                severity = JsonReviewParser.parseSeverity(severityStr),
                file = obj["file"]?.jsonPrimitive?.content,
                line = obj["line"]?.jsonPrimitive?.content?.toIntOrNull(),
                description = description,
                recommendation = obj["recommendation"]?.jsonPrimitive?.content,
            )
        }

        if (summary != null) {
            ReviewSessionHolder.setSummary(summary)
        }
        ReviewSessionHolder.addFindings(findings)

        return ToolResult.Success("Saved ${findings.size} finding(s)")
    }
}
