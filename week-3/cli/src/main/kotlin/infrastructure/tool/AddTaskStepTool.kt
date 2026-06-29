package io.averkhogliad.ai.challenge.week3.cli.infrastructure.tool

import io.averkhogliad.ai.challenge.week3.cli.application.service.TaskStepService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolContext
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolDefinition
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ToolExecutionResult

/**
 * Инструмент `cli::add_task_step` — добавляет шаг в текущую задачу.
 */
class AddTaskStepTool(
    private val taskStepService: TaskStepService,
    private val taskRepository: TaskRepository
) : BaseBuiltinTool() {

    override val definition: BuiltinToolDefinition = BuiltinToolDefinition(
        name = "cli::add_task_step",
        description = "Добавляет шаг в текущую открытую задачу. Используй для планирования конкретных действий.",
        parametersJsonSchema = """
            {
                "type": "object",
                "properties": {
                    "description": {
                        "type": "string",
                        "description": "Описание шага (3-500 символов)"
                    }
                },
                "required": ["description"]
            }
        """.trimIndent()
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolExecutionResult {
        val taskResult = requireTask(context)
        if (taskResult.isFailure) return jsonError(taskResult.exceptionOrNull()!!.message!!)

        val description = arguments["description"] as? String
        if (description.isNullOrBlank()) {
            return jsonError("Ошибка: Описание шага не может быть пустым")
        }
        if (description.length < MIN_STEP_DESCRIPTION_LENGTH) {
            return jsonError("Ошибка: Описание шага слишком короткое (мин. $MIN_STEP_DESCRIPTION_LENGTH символов)")
        }
        if (description.length > MAX_STEP_DESCRIPTION_LENGTH) {
            return jsonError("Ошибка: Описание шага слишком длинное (макс. $MAX_STEP_DESCRIPTION_LENGTH символов)")
        }

        val task = taskResult.getOrThrow()
        val step = taskStepService.addStep(task.id, description)

        val updatedTask = taskRepository.findById(task.id) ?: task
        return ToolExecutionResult(
            text = "✓ Шаг #${step.id} добавлен в задачу #${task.id}: '$description'",
            updatedContext = BuiltinToolContext(currentTask = updatedTask)
        )
    }
}
