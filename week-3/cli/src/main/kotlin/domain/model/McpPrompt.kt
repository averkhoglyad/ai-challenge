package io.averkhogliad.ai.challenge.week3.cli.domain.model

/**
 * Domain-модель MCP Prompt (сценария).
 *
 * ## Архитектурная роль
 * - **Domain Model** — чистое представление MCP Prompt без SDK-зависимостей
 * - **Immutable** — data class
 *
 * @property name Уникальное имя prompt (например, "weather-briefing")
 * @property description Человекочитаемое описание сценария
 * @property arguments Список параметров prompt
 */
data class McpPrompt(
    val name: String,
    val description: String?,
    val arguments: List<PromptArgument>
)

/**
 * Аргумент MCP Prompt.
 *
 * @property name Имя параметра (например, "city", "days")
 * @property description Описание параметра
 * @property required Обязательный ли параметр
 */
data class PromptArgument(
    val name: String,
    val description: String?,
    val required: Boolean = true
)
