package io.averkhogliad.ai.challenge.week3.cli.infrastructure.tool

import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolContext
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolDefinition
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ToolExecutionResult

/**
 * Инструмент `cli::get_current_task` — возвращает детали открытой задачи в JSON.
 */
class GetCurrentTaskTool : BaseBuiltinTool() {

    override val definition: BuiltinToolDefinition = BuiltinToolDefinition(
        name = "cli::get_current_task",
        description = "Возвращает детали текущей открытой задачи: id, название, описание, статус, шаги, привязанное событие",
        parametersJsonSchema = EMPTY_PARAMS_SCHEMA
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolExecutionResult {
        val task = context.currentTask
        if (task == null) {
            return jsonError(NO_OPEN_TASK_MESSAGE)
        }
        return jsonSuccess(task.toJson())
    }

    companion object {
        private const val EMPTY_PARAMS_SCHEMA =
            """{"type":"object","properties":{},"required":[]}"""
    }
}
