package io.averkhogliad.ai.challenge.week3.cli.application.tool

import io.averkhogliad.ai.challenge.week3.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Результат маршрутизации tool call.
 *
 * @property text Текстовый результат для LLM
 * @property updatedContext Обновлённый контекст (если инструмент его изменил)
 */
data class ToolCallResult(
    val text: String,
    val isError: Boolean = false,
    val updatedContext: BuiltinToolContext? = null
)

/**
 * Маршрутизатор tool calls.
 *
 * Поддерживает namespace'ы:
 * - `cli::*` → выполняет через [BuiltinToolExecutor] из [ToolRegistry]
 * - `weather::*`, `events::*`, `notifications::*` → снимает префикс, ищет сервер по [namespaceServerMap], вызывает на нём
 * - без префикса → пробует все подключённые серверы (backward compat)
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация маршрутизации
 */
class ToolCallRouter(
    private val toolRegistry: ToolRegistry,
    private val mcpService: MCPService
) {
    /**
     * Mapping namespace → serverId для MCP-инструментов.
     * Заполняется из [DialogService] при сборе tools.
     */
    @Volatile
    var namespaceServerMap: Map<String, ModelId> = emptyMap()

    companion object {
        /** Разделитель namespace в имени инструмента */
        private const val NAMESPACE_SEPARATOR = "::"
    }

    /**
     * Маршрутизирует вызов инструмента.
     *
     * @param toolName полное имя инструмента (например, "cli::create_task", "weather::resolve_city", или "resolve_city")
     * @param arguments аргументы от LLM
     * @param context текущий контекст исполнения
     * @return результат выполнения
     */
    suspend fun route(
        toolName: String,
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolCallResult {
        if (toolRegistry.isBuiltin(toolName)) {
            return executeBuiltin(toolName, arguments, context)
        }
        if (toolName.contains(NAMESPACE_SEPARATOR)) {
            return executeNamespacedMcp(toolName, arguments)
        }
        return executeMcpFallback(toolName, arguments)
    }

    private suspend fun executeBuiltin(
        toolName: String,
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolCallResult {
        val executor = toolRegistry.findBuiltin(toolName)
        if (executor == null) {
            return ToolCallResult("Ошибка: Встроенный инструмент '$toolName' не найден", isError = true)
        }
        return try {
            val result = executor.execute(arguments, context)
            val isError = result.text.startsWith("Ошибка")
            ToolCallResult(text = result.text, isError = isError, updatedContext = result.updatedContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolCallResult("Ошибка выполнения '$toolName': ${e.message}", isError = true)
        }
    }

    /**
     * Выполняет MCP-инструмент с namespace-префиксом (например, `weather::resolve_city`).
     * Снимает префикс, находит сервер по [namespaceServerMap] и вызывает оригинальное имя.
     */
    private suspend fun executeNamespacedMcp(
        toolName: String,
        arguments: Map<String, Any?>
    ): ToolCallResult {
        val sepIndex = toolName.indexOf(NAMESPACE_SEPARATOR)
        val namespace = toolName.substring(0, sepIndex)
        val originalName = toolName.substring(sepIndex + NAMESPACE_SEPARATOR.length)

        val serverId = namespaceServerMap[namespace]
            ?: return ToolCallResult(
                "Инструмент '$toolName': неймспейс '$namespace' не сопоставлен с сервером",
                isError = true
            )

        return executeOnServer(serverId, originalName, toolName, arguments)
    }

    /**
     * Fallback: перебирает все серверы без namespace (обратная совместимость).
     */
    private suspend fun executeMcpFallback(
        toolName: String,
        arguments: Map<String, Any?>
    ): ToolCallResult {
        return try {
            val servers = mcpService.getAllServersForLlm()
            for (server in servers) {
                if (server.isConnected) {
                    val result = mcpService.callTool(server.id, toolName, arguments)
                    if (result.isSuccess) {
                        return ToolCallResult(result.getOrThrow())
                    }
                }
            }
            ToolCallResult("Инструмент '$toolName' не найден на подключенных серверах", isError = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolCallResult("Ошибка выполнения '$toolName': ${e.message}", isError = true)
        }
    }

    private suspend fun executeOnServer(
        serverId: ModelId,
        originalName: String,
        displayName: String,
        arguments: Map<String, Any?>
    ): ToolCallResult {
        return try {
            val result = mcpService.callTool(serverId, originalName, arguments)
            if (result.isSuccess) {
                ToolCallResult(result.getOrThrow())
            } else {
                val error = result.exceptionOrNull()?.message ?: "неизвестная ошибка"
                ToolCallResult("Инструмент '$displayName' завершился с ошибкой: $error", isError = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolCallResult("Ошибка выполнения '$displayName': ${e.message}", isError = true)
        }
    }
}
