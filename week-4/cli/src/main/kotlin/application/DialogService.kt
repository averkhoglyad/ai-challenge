package io.averkhogliad.ai.challenge.week4.cli.application

import io.averkhogliad.ai.challenge.week4.cli.application.preset.PromptPresetAggregator
import io.averkhogliad.ai.challenge.week4.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolCallRouter
import io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolRegistry
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.*
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException

class DialogService(
    private val llmPort: LlmPort?,
    private val memoryService: MemoryService,
    private val promptBuilder: PromptBuilder,
    private val taskExecutionConfig: TaskExecutionConfig = TaskExecutionConfig(),
    private val profileRepository: ProfileRepository,
    private val invariantService: InvariantService,
    private val mcpService: MCPService,
    private val toolCallRouter: ToolCallRouter,
    private val toolRegistry: ToolRegistry,
    private val promptPresetAggregator: PromptPresetAggregator,
    private val taskRepository: TaskRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Максимальное количество итераций tool calls в одном диалоге */
        const val MAX_TOOL_CALL_ITERATIONS = 20

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
            // Агрегируем пресеты (BUILTIN + MCP)
            val presets = promptPresetAggregator.aggregate()
            // Строим контекст текущей задачи
            var builtinContext = buildContext(taskId)
            val chatMessages = promptBuilder.buildChatMessages(
                workingMemory = memoryContext.workingMemory,
                relevantFacts = memoryContext.relevantFacts,
                recentMessages = memoryContext.recentMessages,
                userInput = userInput,
                profile = activeProfile,
                invariants = invariants,
                mcpPrompts = mcpPrompts,
                presets = presets,
                builtinToolContext = builtinContext
            ).toMutableList()
            // Получаем инструменты: MCP + builtin
            val mcpTools = collectAllTools()
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

                    val args = parseArguments(toolCall.function.arguments)
                    val callResult = toolCallRouter.route(toolName, args, builtinContext)

                    // Обновить контекст, если инструмент его изменил
                    if (callResult.updatedContext != null) {
                        builtinContext = callResult.updatedContext
                    }

                    val toolResult = callResult.text
                    val isError = callResult.isError
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
            val mcpTools = collectMcpToolsRaw()
            llmPort.chat(Prompt(planPrompt), taskExecutionConfig, mcpTools)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskResult.Error("Error plan: ${e.message}", e)
        }
    }

    // ──── Private helpers ────

    /**
     * Строит [BuiltinToolContext] из taskId.
     */
    private suspend fun buildContext(taskId: TaskId?): BuiltinToolContext {
        if (taskId == null) return BuiltinToolContext.EMPTY
        val task = taskRepository.findById(taskId)
        return if (task != null) BuiltinToolContext(currentTask = task) else BuiltinToolContext.EMPTY
    }

    /**
     * Собирает все инструменты (MCP + builtin) и обновляет namespace→server мапу в router-е.
     */
    private suspend fun collectAllTools(): List<MCPTool>? {
        val namespacedMcpTools = collectNamespacedMcpTools()
        val builtinDefs = toolRegistry.getBuiltinDefinitions()
        return if (namespacedMcpTools != null) namespacedMcpTools + builtinDefs else builtinDefs.ifEmpty { null }
    }

    /**
     * Парсит JSON-строку аргументов в Map.
     */
    private fun parseArguments(argumentsJson: String): Map<String, Any?> {
        return try {
            val jsonElement = json.parseToJsonElement(argumentsJson)
            if (jsonElement is JsonObject) {
                jsonElement.mapValues { (_, value) -> parseJsonValue(value) }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            System.err.println("[DIALOG-SERVICE] Ошибка парсинга аргументов JSON: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Собирает MCP-инструменты с namespace-префиксом (weather::, events::, notifications::)
     * и обновляет [ToolCallRouter.namespaceServerMap] для маршрутизации.
     */
    private suspend fun collectNamespacedMcpTools(): List<MCPTool>? {
        val servers = mcpService.getAllServersForLlm()
        if (servers.isEmpty()) {
            System.err.println("${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Нет доступных MCP-серверов")
            toolCallRouter.namespaceServerMap = emptyMap()
            return null
        }

        System.err.println("${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Проверяю ${servers.size} сервер(ов): ${servers.joinToString { "${it.name} (connected=${it.isConnected})" }}")

        val allTools = mutableListOf<MCPTool>()
        val nsMap = mutableMapOf<String, io.averkhogliad.ai.challenge.week4.cli.domain.ModelId>()

        for (server in servers) {
            if (!server.isConnected) {
                System.err.println("  ${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Пропускаю ${server.name} — не подключён")
                continue
            }
            val namespace = deriveNamespace(server.name)
            mcpService.getTools(server.id).onSuccess { tools ->
                nsMap[namespace] = server.id
                System.err.println("  ${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Сервер ${server.name}: найдено ${tools.size} инструмент(ов)")
                for (tool in tools) {
                    val prefixedName = "${namespace}::${tool.name}"
                    val desc =
                        tool.description?.let { d -> if (d.length > 60) d.take(60) + "..." else d } ?: "(нет описания)"
                    System.err.println("    ${ANSI_GREEN}${prefixedName}${ANSI_RESET} — $desc")
                    allTools.add(tool.copy(name = prefixedName))
                }
            }.onFailure { error ->
                System.err.println("  ${ANSI_RED}[MCP-TOOLS]${ANSI_RESET} Ошибка получения инструментов с ${server.name}: ${error.message}")
            }
        }

        toolCallRouter.namespaceServerMap = nsMap
        val total = allTools.size
        if (total > 0) {
            System.err.println("${ANSI_GREEN}[MCP-TOOLS]${ANSI_RESET} Итого собрано инструментов: $total")
        } else {
            System.err.println("${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Инструменты не найдены ни на одном сервере")
        }
        return allTools.ifEmpty { null }
    }

    /**
     * Извлекает namespace из имени системного сервера (напр. "system-weather" → "weather").
     */
    private fun deriveNamespace(serverName: String): String {
        val prefixes = mapOf(
            "system-weather" to "weather",
            "system-events" to "events",
            "system-notifications" to "notifications"
        )
        return prefixes[serverName] ?: serverName
    }

    /**
     * Собирает MCP-инструменты без префиксов (для :plan FSM, backward compat).
     */
    private suspend fun collectMcpToolsRaw(): List<MCPTool>? {
        val servers = mcpService.getAllServersForLlm()
        if (servers.isEmpty()) {
            System.err.println("${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Нет доступных MCP-серверов")
            return null
        }

        System.err.println("${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Проверяю ${servers.size} сервер(ов) для :plan: ${servers.joinToString { "${it.name} (connected=${it.isConnected})" }}")

        val allTools = mutableListOf<MCPTool>()
        for (server in servers) {
            if (!server.isConnected) {
                System.err.println("  ${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Пропускаю ${server.name} — не подключён")
                continue
            }
            mcpService.getTools(server.id).onSuccess { tools ->
                System.err.println("  ${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Сервер ${server.name}: найдено ${tools.size} инструмент(ов)")
                for (tool in tools) {
                    val desc =
                        tool.description?.let { d -> if (d.length > 60) d.take(60) + "..." else d } ?: "(нет описания)"
                    System.err.println("    ${ANSI_GREEN}${tool.name}${ANSI_RESET} — $desc")
                }
                allTools.addAll(tools)
            }.onFailure { error ->
                System.err.println("  ${ANSI_RED}[MCP-TOOLS]${ANSI_RESET} Ошибка получения инструментов с ${server.name}: ${error.message}")
            }
        }
        val total = allTools.size
        if (total > 0) {
            System.err.println("${ANSI_GREEN}[MCP-TOOLS]${ANSI_RESET} Итого собрано инструментов для :plan: $total")
        } else {
            System.err.println("${ANSI_YELLOW}[MCP-TOOLS]${ANSI_RESET} Инструменты не найдены ни на одном сервере")
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
        if (servers.isEmpty()) {
            System.err.println("${ANSI_YELLOW}[MCP-PROMPTS]${ANSI_RESET} Нет доступных MCP-серверов")
            return emptyList()
        }

        System.err.println("${ANSI_YELLOW}[MCP-PROMPTS]${ANSI_RESET} Проверяю ${servers.size} сервер(ов): ${servers.joinToString { "${it.name} (connected=${it.isConnected})" }}")

        val result = mutableListOf<McpPromptInfo>()
        for (server in servers) {
            if (!server.isConnected) {
                System.err.println("  ${ANSI_YELLOW}[MCP-PROMPTS]${ANSI_RESET} Пропускаю ${server.name} — не подключён")
                continue
            }

            val prompts = mcpService.getPrompts(server.id).getOrElse { emptyList() }
            System.err.println("  ${ANSI_YELLOW}[MCP-PROMPTS]${ANSI_RESET} Сервер ${server.name}: найдено ${prompts.size} prompt(ов)")
            for (prompt in prompts) {
                val argsInfo = prompt.arguments.joinToString { "${it.name}(req=${it.required})" }
                System.err.println("    prompt '${prompt.name}': args=[${argsInfo.ifEmpty { "нет" }}]")
                val defaultArgs = prompt.arguments
                    .filter { it.required }
                    .associate { it.name to "{${it.name}}" }

                val messages = if (defaultArgs.isNotEmpty()) {
                    System.err.println("      вызов getPrompt с defaultArgs: $defaultArgs")
                    mcpService.getPrompt(server.id, prompt.name, defaultArgs).getOrElse { emptyList() }
                } else {
                    System.err.println("      вызов getPrompt без аргументов")
                    mcpService.getPrompt(server.id, prompt.name).getOrElse { emptyList() }
                }

                val content = messages
                    .joinToString("\n") { msg ->
                        when (msg.content) {
                            is McpPromptContent.Text -> msg.content.text
                            else -> ""
                        }
                    }

                if (content.isNotBlank()) {
                    System.err.println("      ${ANSI_GREEN}✓${ANSI_RESET} контент: ${content.take(80)}...")
                    result.add(
                        McpPromptInfo(
                            serverId = server.id.value,
                            serverName = server.name,
                            promptName = prompt.name,
                            description = prompt.description,
                            content = content
                        )
                    )
                } else {
                    val roleSummary = messages.groupBy { it.role }.map { "${it.key}: ${it.value.size}" }
                    System.err.println("      ${ANSI_RED}✗${ANSI_RESET} пустой контент (сообщений: ${messages.size}, роли: $roleSummary), пропускаю")
                }
            }
        }
        System.err.println("${ANSI_GREEN}[MCP-PROMPTS]${ANSI_RESET} Итого собрано промптов: ${result.size}")
        return result
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
