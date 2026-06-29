package io.averkhogliad.ai.challenge.week3.cli.infrastructure.tool

import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolContext
import io.averkhogliad.ai.challenge.week3.cli.domain.model.BuiltinToolDefinition
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ToolExecutionResult

/**
 * Инструмент `cli::update_task` — обновляет название и/или описание текущей задачи.
 */
class UpdateTaskTool(
    private val todoTaskService: TodoTaskService,
    private val taskRepository: TaskRepository
) : BaseBuiltinTool() {

    override val definition: BuiltinToolDefinition = BuiltinToolDefinition(
        name = "cli::update_task",
        description = "Обновляет название и/или описание текущей открытой задачи. Требуется указать хотя бы один параметр.",
        parametersJsonSchema = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Новое название задачи"
                    },
                    "description": {
                        "type": "string",
                        "description": "Новое описание задачи"
                    }
                },
                "required": []
            }
        """.trimIndent()
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolExecutionResult {
        val taskResult = requireTask(context)
        if (taskResult.isFailure) return jsonError(taskResult.exceptionOrNull()!!.message!!)

        val newName = arguments["name"] as? String
        val newDescription = arguments["description"] as? String

        if (newName.isNullOrBlank() && newDescription.isNullOrBlank()) {
            return jsonError("Ошибка: Нужно указать хотя бы name или description")
        }

        if (!newName.isNullOrBlank() && newName.length < MIN_TASK_NAME_LENGTH) {
            return jsonError("Ошибка: Название слишком короткое (мин. $MIN_TASK_NAME_LENGTH символов)")
        }
        if (!newName.isNullOrBlank() && newName.length > MAX_TASK_NAME_LENGTH) {
            return jsonError("Ошибка: Название слишком длинное (макс. $MAX_TASK_NAME_LENGTH символов)")
        }
        if (!newDescription.isNullOrBlank() && newDescription.length > MAX_TASK_DESCRIPTION_LENGTH) {
            return jsonError("Ошибка: Описание слишком длинное (макс. $MAX_TASK_DESCRIPTION_LENGTH символов)")
        }

        val task = taskResult.getOrThrow()

        if (!newName.isNullOrBlank()) {
            todoTaskService.editTask(task.id, newName)
        }
        if (!newDescription.isNullOrBlank()) {
            todoTaskService.updateDescription(task.id, newDescription)
        }

        val updatedTask = taskRepository.findById(task.id) ?: task
        return ToolExecutionResult(
            text = "✓ Задача #${task.id} обновлена",
            updatedContext = BuiltinToolContext(currentTask = updatedTask)
        )
    }
}
