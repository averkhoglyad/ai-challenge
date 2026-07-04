package io.averkhogliad.ai.challenge.week4.cli.infrastructure.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskStateExtractor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage as DomainChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatMessage as LlmChatMessage

/**
 * LLM-реализация [TaskStateExtractor] — извлекает дельту [TaskStateDelta]
 * из новых сообщений пользователя с помощью LLM.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует domain-порт [TaskStateExtractor]
 * - Использует [LlmPort] для вызова LLM и kotlinx.serialization для парсинга JSON-ответа
 *
 * ## Логика
 * 1. Формирует промпт с текущим состоянием задачи и JSON-схемой ожидаемого ответа
 * 2. Отправляет в LLM через [LlmPort.chatWithMessages]
 * 3. Парсит JSON-ответ в [TaskStateDeltaDto], затем маппит в [TaskStateDelta.Composite]
 * 4. При ошибке парсинга или LLM — возвращает [Result.failure]
 *
 * @param llmPort порт LLM для отправки запросов
 */
class LlmTaskStateExtractor(
    private val llmPort: LlmPort
) : TaskStateExtractor {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun extract(
        currentState: TaskState,
        newMessages: List<DomainChatMessage>
    ): Result<TaskStateDelta> {
        return try {
            val systemPrompt = buildSystemPrompt(currentState)
            val userContent = buildUserContent(newMessages)

            val messages = listOf(
                LlmChatMessage.system(systemPrompt),
                LlmChatMessage.user(userContent)
            )

            val config = TaskExecutionConfig(temperature = 0.0, maxTokens = 2048)

            val result = llmPort.chatWithMessages(messages, config)
            when (result) {
                is io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Success -> {
                    val deltaDto = parseResponse(result.content)
                    val delta = mapToDomain(deltaDto)
                    Result.success(delta)
                }

                is io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Error ->
                    Result.failure(Exception("LLM error: ${result.message}"))

                is io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Partial ->
                    Result.failure(Exception("LLM returned partial result: ${result.content}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Формирует системный промпт с JSON-схемой и текущим состоянием задачи.
     */
    private fun buildSystemPrompt(currentState: TaskState): String = buildString {
        appendLine("You are a task state extraction assistant. Your job is to analyze the user's new message and extract a delta (set of changes) to the task state.")
        appendLine()
        appendLine("## Current Task State")
        appendLine("Goal: ${currentState.goal ?: "not set"}")
        appendLine()
        appendLine("Defined terms:")
        if (currentState.definedTerms.isEmpty()) {
            appendLine("  (none)")
        } else {
            currentState.definedTerms.forEach { (name, def) ->
                appendLine("  - $name: $def")
            }
        }
        appendLine()
        appendLine("Constraints:")
        if (currentState.constraints.isEmpty()) {
            appendLine("  (none)")
        } else {
            currentState.constraints.forEachIndexed { i, c ->
                appendLine("  [$i] $c")
            }
        }
        appendLine()
        appendLine("Clarified facts:")
        if (currentState.clarifiedFacts.isEmpty()) {
            appendLine("  (none)")
        } else {
            currentState.clarifiedFacts.forEachIndexed { i, f ->
                appendLine("  [$i] $f")
            }
        }
        appendLine()
        appendLine("## Instructions")
        appendLine("Analyze the user's new message. Identify ANY changes to the task state. Output ONLY a JSON object with the following schema:")
        appendLine()
        appendLine("```json")
        appendLine("{")
        appendLine("  \"goalChange\": \"new goal text, or null if unchanged\",")
        appendLine("  \"newTerms\": [{\"name\": \"term name\", \"definition\": \"term definition\"}],")
        appendLine("  \"removedTermNames\": [\"name of term to remove\"],")
        appendLine("  \"newConstraints\": [\"new constraint text\"],")
        appendLine("  \"removedConstraints\": [\"index of constraint to remove as integer\"],")
        appendLine("  \"newClarifiedFacts\": [\"new clarified fact\"]")
        appendLine("}")
        appendLine("```")
        appendLine()
        appendLine("Rules:")
        appendLine("- All arrays can be empty if no changes of that type")
        appendLine("- \"goalChange\" should be null if the goal hasn't changed")
        appendLine("- \"removedConstraints\" contains 0-based indices referencing the current constraints list above")
        appendLine("- \"newClarifiedFacts\" is for facts that the user explicitly clarified (NOT general statements)")
        appendLine("- If nothing changed, return all empty arrays and null goalChange")
        appendLine("- Output ONLY valid JSON, no markdown code fences, no extra text")
    }

    /**
     * Формирует контент пользовательского сообщения из новых domain-сообщений.
     */
    private fun buildUserContent(messages: List<DomainChatMessage>): String = buildString {
        appendLine("## New messages from the user:")
        messages.forEach { msg ->
            val role = when (msg) {
                is DomainChatMessage.User -> "User"
                is DomainChatMessage.Assistant -> "Assistant"
                is DomainChatMessage.System -> "System"
            }
            val text = when (msg) {
                is DomainChatMessage.User -> msg.text
                is DomainChatMessage.Assistant -> msg.text
                is DomainChatMessage.System -> msg.text
            }
            appendLine("[$role]: $text")
        }
    }

    /**
     * Парсит JSON-ответ LLM в [TaskStateDeltaDto].
     */
    private fun parseResponse(content: String): TaskStateDeltaDto {
        val trimmed = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return json.decodeFromString<TaskStateDeltaDto>(trimmed)
    }

    /**
     * Маппит DTO в доменную [TaskStateDelta.Composite].
     */
    private fun mapToDomain(dto: TaskStateDeltaDto): TaskStateDelta {
        val deltas = mutableListOf<TaskStateDelta>()

        // Goal change
        if (dto.goalChange != null) {
            deltas.add(TaskStateDelta.SetGoal(dto.goalChange))
        }

        // New terms
        dto.newTerms.forEach { term ->
            deltas.add(TaskStateDelta.AddTerm(term.name, term.definition))
        }

        // Removed terms
        dto.removedTermNames.forEach { name ->
            deltas.add(TaskStateDelta.RemoveTerm(name))
        }

        // New constraints
        dto.newConstraints.forEach { constraint ->
            deltas.add(TaskStateDelta.AddConstraint(constraint))
        }

        // Removed constraints (by index)
        dto.removedConstraints.forEach { index ->
            deltas.add(TaskStateDelta.RemoveConstraint(index))
        }

        // New clarified facts
        dto.newClarifiedFacts.forEach { fact ->
            deltas.add(TaskStateDelta.AddClarifiedFact(fact))
        }

        return if (deltas.isEmpty()) {
            TaskStateDelta.NoChanges
        } else {
            TaskStateDelta.Composite(deltas)
        }
    }

    /**
     * DTO для десериализации JSON-ответа LLM.
     */
    @Serializable
    private data class TaskStateDeltaDto(
        val goalChange: String? = null,
        val newTerms: List<TermDto> = emptyList(),
        val removedTermNames: List<String> = emptyList(),
        val newConstraints: List<String> = emptyList(),
        val removedConstraints: List<Int> = emptyList(),
        val newClarifiedFacts: List<String> = emptyList()
    )

    @Serializable
    private data class TermDto(
        val name: String,
        val definition: String
    )
}
