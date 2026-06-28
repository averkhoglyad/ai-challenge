package io.averkhogliad.ai.challenge.week3.cli.application

import io.averkhogliad.ai.challenge.week3.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
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

    companion object {
        /** Максимальное количество итераций tool calls в одном диалоге */
        const val MAX_TOOL_CALL_ITERATIONS = 10

        // Префиксы ошибок от executeMcpTool
        private const val MCP_TOOL_ERROR_PREFIX = "Ошибка выполнения"
        private const val MCP_TOOL_NOT_FOUND_PREFIX = "Инструмент"

        // ANSI-коды для визуализации прогресса tool calls
        private const val ANSI_YELLOW = "\u001b[33m"
        private const val ANSI_GREEN = "\u001b[32m"
        private const val ANSI_RED = "\u001b[31m"
        private const val ANSI_RESET = "\u001b[0m"
    }

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
            // Получаем активный профиль для встраивания в промпт
            val activeProfile = profileRepository.findActive()
            // Получаем активные инварианты для встраивания в промпт
            val invariants = invariantService.list()
            // Получаем доступные MCP-сценарии (prompts) со всех серверов
            val mcpPrompts = collectMcpPrompts()
            val chatMessages = promptBuilder.buildChatMessages(
                workingMemory = memoryContext.workingMemory,
                relevantFacts = memoryContext.relevantFacts,
                recentMessages = memoryContext.recentMessages,
                userInput = userInput,
                profile = activeProfile,
                invariants = invariants,
                mcpPrompts = mcpPrompts  // передача MCP-сценариев
            ).toMutableList()
            // Получаем инструменты от подключенных MCP-серверов
            val mcpTools = collectMcpTools()
            memoryService.saveUserMessage(level, taskId, userInput)
            var result = llmPort.chatWithMessages(chatMessages, taskExecutionConfig, mcpTools)

            // Цикл обработки tool_calls
            var remainingIterations = MAX_TOOL_CALL_ITERATIONS
            while (result is TaskResult.Success) {
                val toolCalls = result.toolCalls
                if (toolCalls.isNullOrEmpty() || remainingIterations <= 0) {
                    if (remainingIterations <= 0) {
                        System.err.println("\n${ANSI_YELLOW}[WARN]${ANSI_RESET} Достигнут лимит итераций tool calls ($MAX_TOOL_CALL_ITERATIONS). Возвращаю частичный результат.")
                    }
                    break
                }
                remainingIterations--

                val iteration = MAX_TOOL_CALL_ITERATIONS - remainingIterations
                System.err.println("\n${ANSI_YELLOW}🔄 Итерация $iteration/$MAX_TOOL_CALL_ITERATIONS: вызываю ${toolCalls.size} инструментов...${ANSI_RESET}")

                // Добавить ответ ассистента с tool_calls в историю
                chatMessages.add(ChatMessage.assistantWithToolCalls(result.content, toolCalls))
                if (result.content.isNotBlank()) {
                    memoryService.saveAssistantMessage(level, taskId, result.content)
                }

                // Выполнить каждый tool_call
                for ((index, toolCall) in toolCalls.withIndex()) {
                    if (toolCall.id.isBlank()) {
                        System.err.println("\n  ${ANSI_RED}[WARN]${ANSI_RESET} Пропуск tool call с пустым id: ${toolCall.function.name}")
                        continue
                    }
                    val toolName = toolCall.function.name
                    val argsPreview = toolCall.function.arguments.let {
                        if (it.length > 60) it.take(60) + "..." else it
                    }
                    System.err.println("\n  [${index + 1}/${toolCalls.size}] $toolName($argsPreview)...")

                    val toolResult = executeMcpTool(toolName, toolCall.function.arguments)

                    val isError =
                        toolResult.startsWith(MCP_TOOL_ERROR_PREFIX) || toolResult.startsWith(MCP_TOOL_NOT_FOUND_PREFIX)
                    if (!isError) {
                        val resultPreview = toolResult.let {
                            if (it.length > 80) it.take(80) + "..." else it
                        }.replace("\n", " ")
                        System.err.println("\n    ${ANSI_GREEN}✓${ANSI_RESET} $toolName завершён → $resultPreview")
                    } else {
                        System.err.println("\n    ${ANSI_RED}❌${ANSI_RESET} $toolName: $toolResult")
                    }

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
        val servers = mcpService.getAllServersForLlm()
        if (servers.isEmpty()) return null

        val allTools = mutableListOf<MCPTool>()
        for (server in servers) {
            if (server.isConnected) {
                mcpService.getTools(server.id).onSuccess { tools ->
                    allTools.addAll(tools)
                }
            }
        }
        return allTools.ifEmpty { null }
    }

    /**
     * Собирает MCP-сценарии (prompts) со всех подключённых серверов.
     *
     * Для каждого prompt получает его содержимое с параметрами по умолчанию.
     * Возвращает список [McpPromptInfo], готовый для встраивания в системный промпт.
     */
    private suspend fun collectMcpPrompts(): List<McpPromptInfo> {
        val servers = mcpService.getAllServersForLlm()
        if (servers.isEmpty()) return emptyList()

        val result = mutableListOf<McpPromptInfo>()
        for (server in servers) {
            if (!server.isConnected) continue

            val prompts = mcpService.getPrompts(server.id).getOrElse { emptyList() }
            for (prompt in prompts) {
                val defaultArgs = prompt.arguments
                    .filter { it.required }
                    .associate { it.name to "{${it.name}}" }

                val messages = if (defaultArgs.isNotEmpty()) {
                    mcpService.getPrompt(server.id, prompt.name, defaultArgs).getOrElse { emptyList() }
                } else {
                    mcpService.getPrompt(server.id, prompt.name).getOrElse { emptyList() }
                }

                val content = messages
                    .filter { it.role == MessageRole.USER }
                    .joinToString("\n") { msg ->
                        when (msg.content) {
                            is McpPromptContent.Text -> msg.content.text
                            else -> ""
                        }
                    }

                if (content.isNotBlank()) {
                    result.add(
                        McpPromptInfo(
                            serverId = server.id.value,
                            serverName = server.name,
                            promptName = prompt.name,
                            description = prompt.description,
                            content = content
                        )
                    )
                }
            }
        }
        return result
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
            val servers = mcpService.getAllServersForLlm()
            for (server in servers) {
                if (server.isConnected) {
                    val result = mcpService.callTool(server.id, name, args)
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
