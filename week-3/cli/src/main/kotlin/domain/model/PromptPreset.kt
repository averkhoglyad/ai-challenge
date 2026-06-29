package io.averkhogliad.ai.challenge.week3.cli.domain.model

/**
 * Domain-модель предзаготовленного системного промпта (preset).
 *
 * ## Архитектурная роль
 * - **Domain Model** — чистое представление preset без инфраструктурных зависимостей
 * - **Immutable** — data class
 *
 * @property name Уникальное имя preset (например, "smart-task-planner")
 * @property description Человекочитаемое описание, по которому LLM выбирает preset
 * @property instruction Полный текст инструкции для LLM
 * @property source Источник preset (BUILTIN — из resources, MCP — с сервера)
 * @property tags Теги для категоризации (planning, weather, calendar и т.д.)
 */
data class PromptPreset(
    val name: String,
    val description: String,
    val instruction: String,
    val source: PromptSource,
    val tags: List<String> = emptyList()
)

/**
 * Источник preset.
 */
enum class PromptSource {
    /** Preset, загруженный из resources/presets/ CLI */
    BUILTIN,

    /** Preset, полученный с подключённого MCP-сервера */
    MCP
}
