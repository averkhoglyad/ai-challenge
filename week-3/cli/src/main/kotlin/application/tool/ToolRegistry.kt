package io.averkhogliad.ai.challenge.week3.cli.application.tool

import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool
import io.averkhogliad.ai.challenge.week3.cli.domain.service.BuiltinToolExecutor

/**
 * Реестр всех инструментов (MCP + builtin).
 *
 * Объединяет инструменты с MCP-серверов и встроенные CLI-инструменты.
 * Добавляет неймспейсинг: `cli::` для builtin, оригинальные имена для MCP.
 *
 * ## Архитектурная роль
 * - **Application Layer** — единая точка доступа ко всем инструментам
 */
class ToolRegistry(
    private val builtinExecutors: List<BuiltinToolExecutor>
) {

    /** Все зарегистрированные builtin executors (по имени) */
    private val builtinByName: Map<String, BuiltinToolExecutor> =
        builtinExecutors.associateBy { it.definition.name }

    /**
     * Возвращает все builtin tools в виде [MCPTool] для инжекта в system prompt.
     */
    fun getBuiltinDefinitions(): List<MCPTool> =
        builtinExecutors.map { executor ->
            MCPTool(
                name = executor.definition.name,
                description = executor.definition.description,
                parametersSchema = executor.definition.parametersJsonSchema
            )
        }

    /**
     * Находит builtin executor по полному имени инструмента (например, "cli::create_task").
     *
     * @return executor или null, если не найден
     */
    fun findBuiltin(name: String): BuiltinToolExecutor? = builtinByName[name]

    /**
     * Проверяет, является ли имя инструмента builtin (начинается с `cli::`).
     */
    fun isBuiltin(name: String): Boolean = name.startsWith(BUILTIN_PREFIX)

    companion object {
        const val BUILTIN_PREFIX = "cli::"
    }
}
