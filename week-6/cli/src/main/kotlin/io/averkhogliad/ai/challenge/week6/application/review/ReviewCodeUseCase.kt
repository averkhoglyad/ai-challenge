package io.averkhogliad.ai.challenge.week6.application.review

import io.averkhogliad.ai.challenge.llm.chat.ChatMessage
import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.domain.review.Review
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewFinding
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.util.UUID

class ReviewCodeUseCase(
    private val llmClient: LlmClient,
    private val ragService: RagService? = null,
    private val saveReviewUseCase: SaveReviewUseCase? = null,
) {

    companion object {
        private const val MAX_DIFF_LINES = 5000
    }

    fun execute(
        projectId: String,
        diff: String,
        trigger: ReviewTrigger,
        commitHash: String? = null,
        branch: String? = null,
        sourceBranch: String? = null,
        targetBranch: String? = null,
        prId: String? = null,
    ): Flow<String> = flow {
        ReviewSessionHolder.clear()

        val truncatedDiff = if (diff.lines().size > MAX_DIFF_LINES) {
            diff.lines().take(MAX_DIFF_LINES).joinToString("\n") +
                    "\n\n... (diff truncated, ${diff.lines().size - MAX_DIFF_LINES} more lines)"
        } else {
            diff
        }

        // Get RAG context if available
        val ragContext = try {
            ragService?.search("code review changes $truncatedDiff".take(500))?.joinToString("\n") { it.first }
        } catch (e: Exception) {
            null
        }

        val systemPrompt = buildReviewSystemPrompt(ragContext, truncatedDiff)
        val reviewId = UUID.randomUUID().toString()

        val saveReviewToolDef = buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "save_review")
                put("description", SaveReviewTool().definition.description)
                put("parameters", SaveReviewTool().definition.inputSchema)
            }
        }

        var shouldSave = false
        try {
            emit("🔍 Analyzing code changes...\n")

            val response = llmClient.chat(
                prompt = "Please review the following code diff and provide your findings using the save_review tool.",
                systemPrompt = systemPrompt,
                tools = listOf(saveReviewToolDef),
            )

            if (!response.toolCalls.isNullOrEmpty()) {
                shouldSave = true
                // Tool-calling mode
                val toolMessages = mutableListOf<ChatMessage>()
                val saveTool = SaveReviewTool()

                for (toolCall in response.toolCalls) {
                    val toolName = toolCall.function.name
                    if (toolName == "save_review") {
                        val arguments = try {
                            kotlinx.serialization.json.Json.parseToJsonElement(toolCall.function.arguments)
                                .let { it as? JsonObject } ?: JsonObject(emptyMap())
                        } catch (_: Exception) {
                            JsonObject(emptyMap())
                        }
                        val result = saveTool.execute(arguments)
                        val content = when (result) {
                            is io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult.Success -> result.content
                            is io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult.Error -> "Error: ${result.message}"
                        }
                        toolMessages.add(ChatMessage.tool(toolCall.id, content))
                    }
                }

                // Get follow-up response with summary
                val messages = listOf(
                    ChatMessage.system(systemPrompt),
                    ChatMessage.user("Please review the code diff using save_review tool."),
                    ChatMessage.assistantWithToolCalls(response.content ?: "", response.toolCalls.orEmpty()),
                ) + toolMessages + listOf(
                    ChatMessage.user("Please provide a brief summary of your review in the response.")
                )

                val finalResponse = llmClient.chatWithMessages(messages, tools = null)

                if (finalResponse.content != null) {
                    ReviewSessionHolder.setSummary(finalResponse.content!!)
                    emit("✅ Review completed.\n")
                    emit(finalResponse.content!!)
                }
            } else if (response.content != null) {
                shouldSave = true
                // JSON fallback mode
                val content = response.content!!
                val findings = JsonReviewParser.parseFindings(content)
                val summary = JsonReviewParser.parseSummary(content)

                if (findings.isNotEmpty()) {
                    ReviewSessionHolder.addFindings(findings)
                    if (summary != null) {
                        ReviewSessionHolder.setSummary(summary)
                    }
                    emit("✅ Review completed (JSON mode). Found ${findings.size} issue(s).\n")
                    findings.take(5).forEach { f ->
                        emit("  - [${f.severity}] ${f.category}: ${f.description.take(80)}...\n")
                    }
                    if (findings.size > 5) {
                        emit("  ... and ${findings.size - 5} more. Use /review show $reviewId for details.\n")
                    }
                } else {
                    emit("⚠ LLM response did not contain structured findings. Raw response:\n")
                    emit(content.take(500))
                }
            }

        } catch (e: Exception) {
            emit("❌ Review failed: ${e.message}\n")
        } finally {
            // Collect findings even if follow-up messages failed
            val (findings, summary) = ReviewSessionHolder.collect()

            val review = Review(
                id = reviewId,
                projectId = projectId,
                trigger = trigger,
                commitHash = commitHash,
                branch = branch,
                sourceBranch = sourceBranch,
                targetBranch = targetBranch,
                prId = prId,
                diff = diff,
                summary = summary,
                findings = findings,
            )

            if (shouldSave) {
                try {
                    saveReviewUseCase?.execute(review)
                    emit("\n📋 Review ID: $reviewId\n")
                } catch (e: Exception) {
                    emit("\n⚠ Review completed but failed to save: ${e.message}\n")
                    emit("📋 Review ID: $reviewId\n")
                }
            }
        }
    }

    private fun buildReviewSystemPrompt(ragContext: String?, diff: String): String = buildString {
        appendLine("You are a senior code reviewer. Analyze the provided diff and identify issues.")
        appendLine()
        appendLine("Categories: BUG, ARCHITECTURE, PERFORMANCE, SECURITY, BEST_PRACTICE, READABILITY, MAINTAINABILITY, OTHER")
        appendLine("Severities: CRITICAL, WARNING, INFO")
        appendLine()
        appendLine("Instructions:")
        appendLine("1. Focus on real issues — avoid false positives")
        appendLine("2. For each finding, specify category, severity, file path, line number if visible in diff, description, and recommendation")
        appendLine("3. Use the save_review tool to report your findings")
        appendLine("4. If you cannot use the tool, respond with JSON: {\"findings\": [...], \"summary\": \"...\"}")
        appendLine()

        if (ragContext != null) {
            appendLine("Related codebase context (for reference):")
            appendLine("```")
            appendLine(ragContext.take(2000))
            appendLine("```")
            appendLine()
        }

        appendLine("--- DIFF TO REVIEW ---")
        appendLine("```diff")
        appendLine(diff.take(8000))
        appendLine("```")
    }
}
