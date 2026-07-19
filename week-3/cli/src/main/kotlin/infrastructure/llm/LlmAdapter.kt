package io.averkhogliad.ai.challenge.week3.cli.infrastructure.llm

import io.averkhogliad.ai.challenge.llm.chat.*
import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.DomainFunctionCall
import io.averkhogliad.ai.challenge.week3.cli.domain.model.DomainToolCall
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ChatRole
import io.averkhogliad.ai.challenge.week3.cli.domain.service.LlmPort
import kotlinx.serialization.json.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ChatMessage as DomainChatMessage

/**
 * Адаптер, реализующий domain-интерфейс [LlmPort] с помощью infrastructure [LlmClient].
 *
 * Реализует паттерн Adapter из чистой архитектуры (Clean Architecture):
 * - domain-слой определяет интерфейс [LlmPort] и НЕ зависит от infrastructure
 * - infrastructure-слой НЕ зависит от domain (зависимость через интерфейс в domain)
 * - [LlmAdapter] находится в infrastructure-слое и связывает оба слоя через маппинг моделей
 *
 * ## Маппинг моделей
 *
 * | Domain | Infrastructure |
 * |--------|---------------|
 * | [Prompt] | String (prompt.value) |
 * | [ModelId] | String (modelId.value) |
 * | [TaskExecutionConfig] | [ChatParameters] |
 * | [DomainChatMessage] | [ChatMessage] |
 * | [TaskResult] | [ChatResponse] |
 *
 * ## Обработка ошибок
 *
 * - [LlmException] (кидается [LlmClient] при ошибках API/сети) → [TaskResult.Error]
 * - Прочие исключения → [TaskResult.Error] с сообщением "Unexpected error"
 * - [CancellationException] НЕ перехватывается — пробрасывается для корректной работы structured concurrency
 *
 * @property llmClient инфраструктурный клиент для HTTP-запросов к LLM API
 * @property defaultModelId модель по умолчанию (используется, если [TaskExecutionConfig.modelId] == null)
 */
class LlmAdapter(
    private val llmClient: LlmClient,
    private val defaultModelId: ModelId,
    private val availableModels: List<ModelId> = listOf(defaultModelId)
) : LlmPort {

    override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig, tools: List<MCPTool>?): TaskResult {
        val messages = listOf(
            DomainChatMessage.user(prompt.value)
        )
        return chatWithMessages(messages, config, tools)
    }

    override suspend fun chatWithMessages(
        messages: List<DomainChatMessage>,
        config: TaskExecutionConfig,
        tools: List<MCPTool>?
    ): TaskResult {
        return executeLlmCall {
            val params = mapToChatParameters(config)
            val model = resolveModelId(config.modelId)
            val infraMessages = mapToInfrastructureChatMessages(messages)
            val jsonTools = tools?.let { convertTools(it) }
            llmClient.chatWithMessages(
                messages = infraMessages,
                parameters = params,
                model = model,
                tools = jsonTools,
            )
        }
    }

    override suspend fun listModels(): List<ModelId> = availableModels

    // ──── Private helpers ────

    /**
     * Шаблон обработки LLM-вызова:
     *  - успешный [ChatResponse] → [TaskResult] через [mapToDomain]
     *  - [LlmException] → [TaskResult.Error]
     *  - прочие исключения → [TaskResult.Error]
     *  - [kotlinx.coroutines.CancellationException] пробрасывается без изменений
     */
    private suspend fun executeLlmCall(call: suspend () -> ChatResponse): TaskResult {
        return try {
            val response = call()
            mapToDomain(response)
        } catch (e: LlmException) {
            TaskResult.Error(e.message ?: "LLM error", e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskResult.Error("Unexpected error: ${e.message}", e)
        }
    }

    /** Определяет итоговую модель: конфиг задачи → defaultModelId. */
    private fun resolveModelId(configModelId: ModelId?): String {
        return (configModelId ?: defaultModelId).value
    }

    /** Маппинг [TaskExecutionConfig] → [ChatParameters]. */
    private fun mapToChatParameters(config: TaskExecutionConfig): ChatParameters {
        return ChatParameters(
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            stop = config.stopSequences.ifEmpty { null }
        )
    }

    /** Маппинг domain [DomainChatMessage] → infrastructure [ChatMessage]. */
    private fun mapToInfrastructureChatMessages(
        domainMessages: List<DomainChatMessage>
    ): List<ChatMessage> {
        return domainMessages.mapNotNull { dm ->
            when {
                dm.role == ChatRole.TOOL -> {
                    val callId = dm.toolCallId
                    if (callId.isNullOrBlank()) {
                        System.err.println(
                            "[WARN] Skipping tool message with empty tool_call_id, content: ${
                                dm.content.take(
                                    50
                                )
                            }"
                        )
                        null
                    } else {
                        ChatMessage.tool(toolCallId = callId, content = dm.content)
                    }
                }

                dm.toolCalls != null -> {
                    ChatMessage.assistantWithToolCalls(
                        content = dm.content,
                        toolCalls = mapDomainToolCalls(dm.toolCalls) ?: emptyList()
                    )
                }

                else -> ChatMessage(role = dm.role.roleName, content = dm.content)
            }
        }
    }

    // ──── MCP Tools conversion ────

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Конвертирует список [MCPTool] в OpenAI-совместимый формат tools.
     *
     * Каждый MCPTool преобразуется в JsonObject вида:
     * ```json
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "...",
     *     "description": "...",
     *     "parameters": { "type": "object", "properties": {...}, "required": [...] }
     *   }
     * }
     * ```
     *
     * Имена инструментов санируются: `::` → `__`, т.к. OpenAI API
     * разрешает в function.name только [a-zA-Z0-9_-].
     */
    private fun convertTools(tools: List<MCPTool>): List<JsonObject> {
        return tools.map { tool ->
            val schema = json.parseToJsonElement(tool.parametersSchema).jsonObject
            buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", sanitizeToolNameForApi(tool.name))
                    tool.description?.let { put("description", it) }
                    put("parameters", buildJsonObject {
                        put("type", schema["type"] ?: JsonPrimitive("object"))
                        schema["properties"]?.let { put("properties", it) }
                        schema["required"]?.let { put("required", it) }
                    })
                })
            }
        }
    }

    /** Заменяет `::` → `__` для соответствия OpenAI function.name паттерну [a-zA-Z0-9_-]. */
    private fun sanitizeToolNameForApi(name: String): String = name.replace("::", "__")

    /** Обратная замена `__` → `::` при получении tool call от LLM. */
    private fun desanitizeToolNameFromApi(name: String): String = name.replace("__", "::")

    /** Маппинг infrastructure [ChatResponse] → domain [TaskResult]. */
    private fun mapToDomain(response: ChatResponse): TaskResult {
        val metadata = mutableMapOf<String, Any>(
            "finishReason" to (response.finishReason ?: "unknown")
        )
        response.usage?.let { usage ->
            metadata["promptTokens"] = usage.promptTokens
            metadata["completionTokens"] = usage.completionTokens
            metadata["totalTokens"] = usage.totalTokens
        }

        return when {
            response.isFiltered() -> TaskResult.Error("Response was blocked by content filter")
            response.isTruncated() -> TaskResult.Partial(content = response.content ?: "", progress = 1.0)
            else -> TaskResult.Success(
                content = response.content ?: "",
                metadata = metadata,
                toolCalls = mapToolCalls(response.toolCalls)
            )
        }
    }

    /** Маппинг utils [ToolCall] → domain [DomainToolCall]. */
    private fun mapToolCalls(utilsToolCalls: List<ToolCall>?): List<DomainToolCall>? =
        utilsToolCalls?.map {
            DomainToolCall(
                it.id,
                it.type,
                DomainFunctionCall(desanitizeToolNameFromApi(it.function.name), it.function.arguments)
            )
        }

    /** Маппинг domain [DomainToolCall] → utils [ToolCall]. */
    private fun mapDomainToolCalls(domainToolCalls: List<DomainToolCall>?): List<ToolCall>? =
        domainToolCalls?.map {
            ToolCall(it.id, it.type, FunctionCall(sanitizeToolNameForApi(it.function.name), it.function.arguments))
        }
}
