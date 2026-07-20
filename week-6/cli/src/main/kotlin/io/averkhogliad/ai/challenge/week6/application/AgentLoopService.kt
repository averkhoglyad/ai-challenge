package io.averkhogliad.ai.challenge.week6.application

import io.averkhogliad.ai.challenge.llm.chat.ChatMessage
import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class AgentLoopService(
    private val llmClient: LlmClient,
    private val toolRegistry: ToolRegistry,
    private val projectContextProvider: ProjectContextProvider,
) {
    private companion object {
        const val NO_ANSWER_MESSAGE =
            "Не могу ответить на этот вопрос по доступной информации проекта. Уточните вопрос или добавьте документацию."
    }

    fun processQuery(
        query: String,
        systemPromptOverride: String? = null,
        excludeExplicitTools: Boolean = false,
        maxToolCalls: Int = 20,
    ): Flow<String> = flow {
        val ctxResult = projectContextProvider.getContext()
        val ctx = when (ctxResult) {
            is DomainResult.Success -> ctxResult.value
            is DomainResult.Failure -> {
                emit("Ошибка получения контекста проекта: ${ctxResult.error.message}")
                return@flow
            }
        }

        val systemPrompt = systemPromptOverride ?: buildSystemPrompt(ctx, excludeExplicitTools)
        val allDefinitions = if (excludeExplicitTools) {
            toolRegistry.getDefinitions().filter { !it.requiresExplicitInvocation }
        } else {
            toolRegistry.getDefinitions()
        }
        val toolDefinitions = allDefinitions.map { definition ->
            buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", definition.name)
                    put("description", definition.description)
                    put("parameters", definition.inputSchema)
                }
            }
        }

        try {
            val response = llmClient.chat(
                prompt = query,
                systemPrompt = systemPrompt,
                tools = toolDefinitions.ifEmpty { null },
            )

            if (!response.toolCalls.isNullOrEmpty()) {
                var toolCallCount = 0
                // Execute tool calls
                val toolMessages = mutableListOf<ChatMessage>()
                for (toolCall in response.toolCalls) {
                    if (toolCallCount >= maxToolCalls) {
                        toolMessages.add(
                            ChatMessage.tool(
                                toolCall.id,
                                "Error: Maximum tool calls ($maxToolCalls) exceeded"
                            )
                        )
                        continue
                    }
                    toolCallCount++
                    val toolName = toolCall.function.name
                    val tool = toolRegistry.getTool(toolName)
                    if (tool != null) {
                        val arguments = try {
                            kotlinx.serialization.json.Json.parseToJsonElement(toolCall.function.arguments)
                                .let { it as? JsonObject } ?: JsonObject(emptyMap())
                        } catch (_: Exception) {
                            JsonObject(emptyMap())
                        }
                        val result = tool.execute(arguments)
                        val content = when (result) {
                            is io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult.Success -> result.content
                            is io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult.Error -> "Error: ${result.message}"
                            is io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult.PendingConfirm -> "Operation requires confirmation: ${result.message}"
                        }
                        toolMessages.add(ChatMessage.tool(toolCall.id, content))
                    } else {
                        toolMessages.add(ChatMessage.tool(toolCall.id, "Error: Tool '$toolName' is not available"))
                    }
                }

                // Second LLM call with tool results
                val messages = listOf(
                    ChatMessage.system(systemPrompt),
                    ChatMessage.user(query),
                    ChatMessage.assistantWithToolCalls(response.content ?: "", response.toolCalls.orEmpty()),
                ) + toolMessages

                val finalResponse = llmClient.chatWithMessages(
                    messages = messages,
                    tools = toolDefinitions.ifEmpty { null },
                )

                emit(finalResponse.content.orFallback())
            } else {
                emit(response.content.orFallback())
            }
        } catch (e: Exception) {
            emit("Ошибка при обработке запроса: ${e.message}")
        }
    }

    private fun buildSystemPrompt(
        ctx: io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext?,
        excludeExplicitTools: Boolean = false,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Ты помощник, отвечающий на вопросы о структуре и документации проекта.")
        sb.appendLine(
            "Для вопросов о конкретном проекте, его файлах, структуре, коде или документации сначала используй " +
                    "подходящие read-only инструменты: list_files, search_code, read_file и search_docs. " +
                    "Не отвечай по предположениям."
        )
        sb.appendLine(
            "Никогда не оставляй ответ пустым. Сообщай о недостатке информации только после попытки получить её " +
                    "доступными инструментами; предложи уточнить вопрос или добавить документацию."
        )
        sb.appendLine(
            "Подкрепляй каждый содержательный вывод ссылкой на файл в формате `path/to/file`. " +
                    "Для выводов по документации обязательно указывай также номер строки в формате `path/to/file:123`."
        )

        if (ctx != null) {
            sb.appendLine()
            sb.appendLine("Информация о проекте:")
            sb.appendLine("- Корневая директория: ${ctx.rootPath}")
            if (ctx.isGitEnabled) {
                sb.appendLine("- Git: доступен")
            } else {
                sb.appendLine("- Git: недоступен")
            }
            if (ctx.docsPaths.isNotEmpty()) {
                sb.appendLine("- Документация доступна по путям: ${ctx.docsPaths.joinToString(", ")}")
            }
        }

        val toolDefs = if (excludeExplicitTools) {
            toolRegistry.getDefinitions().filter { !it.requiresExplicitInvocation }
        } else {
            toolRegistry.getDefinitions()
        }
        if (toolDefs.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Доступные инструменты:")
            toolDefs.forEach { tool ->
                sb.appendLine("- ${tool.name}: ${tool.description}")
            }
            sb.appendLine()
            sb.appendLine("Используй инструменты для получения актуальной информации о проекте.")
        }

        return sb.toString()
    }

    private fun String?.orFallback(): String =
        takeIf { !it.isNullOrBlank() } ?: NO_ANSWER_MESSAGE
}
