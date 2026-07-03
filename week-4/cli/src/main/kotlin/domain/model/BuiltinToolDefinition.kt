package io.averkhogliad.ai.challenge.week4.cli.domain.model

/**
 * Определение встроенного CLI-инструмента, доступного LLM.
 *
 * ## Архитектурная роль
 * - **Domain Model** — метаданные инструмента без инфраструктурных зависимостей
 *
 * @property name Полное имя с неймспейсом (например, "cli::create_task")
 * @property description Человекочитаемое описание для LLM
 * @property parametersJsonSchema JSON Schema параметров в виде строки
 */
data class BuiltinToolDefinition(
    val name: String,
    val description: String,
    val parametersJsonSchema: String
)
