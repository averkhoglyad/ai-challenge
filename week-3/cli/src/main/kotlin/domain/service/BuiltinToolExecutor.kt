package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolContext
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolDefinition

/**
 * Результат выполнения builtin инструмента.
 *
 * @property text Текстовый результат для LLM
 * @property updatedContext Обновлённый контекст (null, если контекст не изменился)
 */
data class ToolExecutionResult(
    val text: String,
    val updatedContext: BuiltinToolContext? = null
)

/**
 * Порт для выполнения встроенных CLI-инструментов.
 *
 * ## Архитектурная роль
 * - **Domain Port** — контракт, реализуемый infrastructure-слоем
 * - **Functional Core / Imperative Shell** — infrastructure impl содержит I/O
 *
 * Каждый конкретный инструмент реализует этот интерфейс.
 */
interface BuiltinToolExecutor {
    /** Метаданные инструмента */
    val definition: BuiltinToolDefinition

    /**
     * Выполняет инструмент с переданными аргументами и контекстом.
     *
     * @param arguments Именованные аргументы от LLM
     * @param context Текущий контекст исполнения
     * @return результат выполнения
     */
    suspend fun execute(
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolExecutionResult
}
