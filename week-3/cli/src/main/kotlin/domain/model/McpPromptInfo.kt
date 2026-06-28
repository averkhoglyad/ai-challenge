package io.averkhogliad.ai.challenge.week3.cli.domain.model

/**
 * Агрегированная информация о MCP Prompt для встраивания в системный промпт.
 *
 * ## Архитектурная роль
 * - **Domain Model** — DTO для передачи данных между Application и Domain слоями
 * - **Immutable** — data class
 *
 * @property serverId Идентификатор MCP-сервера
 * @property serverName Имя MCP-сервера
 * @property promptName Имя prompt
 * @property description Описание сценария
 * @property content Текст инструкции (после подстановки параметров по умолчанию)
 */
data class McpPromptInfo(
    val serverId: String,
    val serverName: String,
    val promptName: String,
    val description: String?,
    val content: String
)
