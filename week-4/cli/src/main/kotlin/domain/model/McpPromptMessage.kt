package io.averkhogliad.ai.challenge.week4.cli.domain.model

/**
 * Domain-модель сообщения MCP Prompt.
 *
 * ## Архитектурная роль
 * - **Domain Model** — чистое представление без SDK-зависимостей
 * - **Immutable** — data class
 *
 * @property role Роль отправителя (USER, ASSISTANT, SYSTEM)
 * @property content Содержимое сообщения
 */
data class McpPromptMessage(
    val role: MessageRole,
    val content: McpPromptContent
)

/**
 * Содержимое сообщения MCP Prompt.
 *
 * Sealed interface для поддержки разных типов контента.
 * Текущая реализация поддерживает только текстовый контент,
 * остальные типы помечаются как [Unsupported].
 */
sealed interface McpPromptContent {
    /** Текстовое содержимое */
    data class Text(val text: String) : McpPromptContent

    /** Неподдерживаемый тип контента (изображения, ресурсы и т.д.) */
    data object Unsupported : McpPromptContent
}
