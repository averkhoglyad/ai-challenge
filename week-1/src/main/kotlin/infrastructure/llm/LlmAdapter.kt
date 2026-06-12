package io.averkhogliad.ai.challenge.week1.infrastructure.llm

import io.averkhogliad.ai.challenge.utils.llm.*
import io.averkhogliad.ai.challenge.week1.domain.ModelId
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage as DomainChatMessage

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

    override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return executeLlmCall {
            val params = mapToChatParameters(config)
            val model = resolveModelId(config.modelId)
            llmClient.chat(
                prompt = prompt.value,
                systemPrompt = null,
                parameters = params,
                model = model
            )
        }
    }

    override suspend fun chatWithMessages(
        messages: List<DomainChatMessage>,
        config: TaskExecutionConfig
    ): TaskResult {
        return executeLlmCall {
            val params = mapToChatParameters(config)
            val model = resolveModelId(config.modelId)
            val infraMessages = mapToInfrastructureChatMessages(messages)
            llmClient.chatWithMessages(
                messages = infraMessages,
                parameters = params,
                model = model
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
        return domainMessages.map { ChatMessage(role = it.role.roleName, content = it.content) }
    }

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
            response.isTruncated() -> TaskResult.Partial(content = response.content, progress = 1.0)
            else -> TaskResult.Success(content = response.content, metadata = metadata)
        }
    }
}
