package io.averkhogliad.ai.challenge.week3.cli.application

import io.averkhogliad.ai.challenge.week3.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.*
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException

class DialogService(
    private val llmPort: LlmPort?,
    private val memoryService: MemoryService,
    private val promptBuilder: PromptBuilder,
    private val taskExecutionConfig: TaskExecutionConfig = TaskExecutionConfig(),
    private val profileRepository: ProfileRepository,  // доступ к активному профилю для встраивания в промпт
    private val invariantService: InvariantService,  // доступ к инвариантам для встраивания в промпт (CachingInvariantService extends InvariantService)
    private val mcpService: MCPService  // доступ к MCP-инструментам для передачи в LLM
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(
        userInput: String,
        level: SessionLevel,
        taskId: TaskId? = null
    ): TaskResult {
        if (llmPort == null) {
            return TaskResult.Error("LLM не настроен. Добавьте API-ключ в конфигурацию.")
        }
        return try {
            val memoryContext = memoryService.getFullMemoryContext(
                level = level,
                taskId = taskId,
                userQuery = userInput,
                factSearchLimit = 5
            )
            // NEW: получаем активный профиль для встраивания в промпт
            val activeProfile = profileRepository.findActive()
            // NEW: получаем активные инварианты для встраивания в промпт
            val invariants = invariantService.list()
            val chatMessages = promptBuilder.buildChatMessages(
                workingMemory = memoryContext.workingMemory,
                relevantFacts = memoryContext.relevantFacts,
                recentMessages = memoryContext.recentMessages,
                userInput = userInput,
                profile = activeProfile,  // NEW: передача активного профиля
                invariants = invariants  // NEW: передача инвариантов
            ).toMutableList()
            // NEW: получаем инструменты от подключенных MCP-серверов
            val mcpTools = collectMcpTools()
            memoryService.saveUserMessage(level, taskId, userInput)
            var result = llmPort.chatWithMessages(chatMessages, taskExecutionConfig, mcpTools)

            // Цикл обработки tool_calls
            var maxIterations = 5
            while (result is TaskResult.Success) {
                val toolCalls = result.toolCalls
                if (toolCalls.isNullOrEmpty() || maxIterations-- <= 0) break

                // Добавить ответ ассистента с tool_calls в историю
                chatMessages.add(ChatMessage.assistantWithToolCalls(result.content, toolCalls))
                if (result.content.isNotBlank()) {
                    memoryService.saveAssistantMessage(level, taskId, result.content)
                }

                // Выполнить каждый tool_call
                for (toolCall in toolCalls) {
                    if (toolCall.id.isBlank()) {
                        System.err.println("[WARN] Skipping tool call with empty id: ${toolCall.function.name}")
                        continue
                    }
                    val toolResult = executeMcpTool(toolCall.function.name, toolCall.function.arguments)
                    chatMessages.add(ChatMessage.tool(toolCall.id, toolResult))
                }

                // Отправить результаты обратно в LLM
                result = llmPort.chatWithMessages(chatMessages, taskExecutionConfig, mcpTools)
            }

            if (result is TaskResult.Success) {
                memoryService.saveAssistantMessage(level, taskId, result.content)
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskResult.Error("Error LLM: ${e.message}", e)
        }
    }

    suspend fun planSteps(
        taskTitle: String,
        taskDescription: String? = null,
        level: SessionLevel,
        taskId: TaskId? = null
    ): TaskResult {
        if (llmPort == null) {
            return TaskResult.Error("LLM не настроен. Добавьте API-ключ в конфигурацию.")
        }
        return try {
            val memoryContext = memoryService.getFullMemoryContext(
                level = level,
                taskId = taskId,
                userQuery = taskTitle,
                factSearchLimit = 3
            )
            // получаем активные инварианты для встраивания в промпт планирования
            val invariants = invariantService.list()
            val planPrompt = promptBuilder.buildPlanPrompt(
                taskTitle = taskTitle,
                taskDescription = taskDescription,
                workingMemory = memoryContext.workingMemory,
                relevantFacts = memoryContext.relevantFacts,
                invariants = invariants  // NEW: передача инвариантов
            )
            val mcpTools = collectMcpTools()
            llmPort.chat(Prompt(planPrompt), taskExecutionConfig, mcpTools)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskResult.Error("Error plan: ${e.message}", e)
        }
    }

    // ──── Private helpers ────

    /**
     * Собирает инструменты со всех подключенных MCP-серверов.
     *
     * Если серверов нет или ни один не подключен — возвращает null.
     * Ошибки получения инструментов с отдельных серверов игнорируются.
     */
    private suspend fun collectMcpTools(): List<MCPTool>? {
        val servers = mcpService.listServers()
        if (servers.isEmpty()) return null

        val allTools = mutableListOf<MCPTool>()
        for (server in servers) {
            if (server.status is MCPConnectionState.Connected) {
                mcpService.getTools(server.config.id).onSuccess { tools ->
                    allTools.addAll(tools)
                }
            }
        }
        return allTools.ifEmpty { null }
    }

    /**
     * Выполняет MCP-инструмент и возвращает текстовый результат.
     *
     * Ищет инструмент на всех подключенных MCP-серверах.
     *
     * @param name имя инструмента
     * @param argumentsJson аргументы в формате JSON-строки
     * @return текстовый результат или сообщение об ошибке
     */
    private suspend fun executeMcpTool(name: String, argumentsJson: String): String {
        return try {
            val jsonElement = json.parseToJsonElement(argumentsJson)
            val args = if (jsonElement is JsonObject) {
                jsonElement.mapValues { (_, value) -> parseJsonValue(value) }
            } else {
                emptyMap<String, Any?>()
            }
            val servers = mcpService.listServers()
            for (server in servers) {
                if (server.status is MCPConnectionState.Connected) {
                    val result = mcpService.callTool(server.config.id, name, args)
                    if (result.isSuccess) return result.getOrThrow()
                }
            }
            "Инструмент '$name' не найден на подключенных серверах"
        } catch (e: Exception) {
            "Ошибка выполнения '$name': ${e.message}"
        }
    }

    /**
     * Рекурсивно преобразует [JsonElement] в нативный Kotlin-тип.
     */
    private fun parseJsonValue(value: JsonElement): Any? = when (value) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            value.isString -> value.content
            value.content == "true" -> true
            value.content == "false" -> false
            value.content.contains('.') -> value.content.toDoubleOrNull() ?: value.content
            else -> value.content.toLongOrNull() ?: value.content
        }

        is JsonArray -> value.map { parseJsonValue(it) }
        is JsonObject -> value.mapValues { (_, v) -> parseJsonValue(v) }
    }
}
