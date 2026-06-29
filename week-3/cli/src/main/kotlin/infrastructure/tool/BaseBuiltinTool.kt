package io.averkhogliad.ai.challenge.week3.cli.infrastructure.tool

import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolContext
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.service.BuiltinToolExecutor
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ToolExecutionResult

/**
 * Базовый класс для builtin tools, работающих с текущей задачей.
 *
 * Предоставляет:
 * - [requireTask] — извлечение текущей задачи из контекста
 * - [buildJsonSuccess] / [buildJsonError] — форматирование JSON-ответов
 */
abstract class BaseBuiltinTool : BuiltinToolExecutor {

    /**
     * Извлекает текущую задачу из контекста.
     *
     * @return [Result.success] с задачей или [Result.failure] с сообщением об ошибке
     */
    protected fun requireTask(context: BuiltinToolContext): Result<Task> {
        val task = context.currentTask
        return if (task != null) {
            Result.success(task)
        } else {
            Result.failure(IllegalStateException(NO_OPEN_TASK_MESSAGE))
        }
    }

    protected fun jsonSuccess(message: String): ToolExecutionResult =
        ToolExecutionResult(message)

    protected fun jsonError(message: String): ToolExecutionResult =
        ToolExecutionResult(message)

    companion object {
        const val NO_OPEN_TASK_MESSAGE =
            "Ошибка: Нет открытой задачи. Используй cli::create_task для создания или :open <id> в CLI."

        // Validation bounds (соответствуют спецификации tools)
        const val MIN_TASK_NAME_LENGTH = 3
        const val MAX_TASK_NAME_LENGTH = 200
        const val MAX_TASK_DESCRIPTION_LENGTH = 2000
        const val MIN_STEP_DESCRIPTION_LENGTH = 3
        const val MAX_STEP_DESCRIPTION_LENGTH = 500
    }
}
