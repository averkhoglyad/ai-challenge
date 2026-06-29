package io.averkhogliad.ai.challenge.week3.cli.infrastructure.tool

import io.averkhogliad.ai.challenge.week3.cli.application.service.TaskStepService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolContext
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolDefinition
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ToolExecutionResult

/**
 * Инструмент `cli::list_task_steps` — возвращает список шагов текущей задачи.
 */
class ListTaskStepsTool(
    private val taskStepService: TaskStepService
) : BaseBuiltinTool() {

    override val definition: BuiltinToolDefinition = BuiltinToolDefinition(
        name = "cli::list_task_steps",
        description = "Возвращает список шагов текущей открытой задачи с их статусами выполнения",
        parametersJsonSchema = EMPTY_PARAMS_SCHEMA
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolExecutionResult {
        val taskResult = requireTask(context)
        if (taskResult.isFailure) return jsonError(taskResult.exceptionOrNull()!!.message!!)

        val task = taskResult.getOrThrow()
        val steps = taskStepService.listSteps(task.id)

        return jsonSuccess(steps.toJson(task))
    }

    companion object {
        private const val EMPTY_PARAMS_SCHEMA =
            """{"type":"object","properties":{},"required":[]}"""
    }
}

private fun List<TaskStep>.toJson(task: io.averkhogliad.ai.challenge.week3.cli.domain.model.Task): String =
    buildString {
        appendLine("{")
        appendLine("""  "taskId": "${task.id}",""")
        appendLine("""  "taskName": "${task.title.escapeJson()}",""")
        appendLine("""  "steps": [""")
        forEachIndexed { index, step: TaskStep ->
            append("    {")
            append("\"id\": \"${step.id}\", ")
            append("\"order\": ${step.order}, ")
            append("\"description\": \"${step.text.escapeJson()}\", ")
            append("\"completed\": ${step.isCompleted}")
            append("}")
            if (index < size - 1) append(",")
            appendLine()
        }
        appendLine("  ]")
        appendLine("}")
    }
