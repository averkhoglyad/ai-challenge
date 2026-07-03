package io.averkhogliad.ai.challenge.week4.cli.infrastructure.tool

import io.averkhogliad.ai.challenge.week4.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week4.cli.domain.model.BuiltinToolContext
import io.averkhogliad.ai.challenge.week4.cli.domain.model.BuiltinToolDefinition
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ToolExecutionResult

/**
 * Инструмент `cli::create_task` — создаёт новую задачу и устанавливает её как текущую.
 */
class CreateTaskTool(
    private val todoTaskService: TodoTaskService,
    private val taskRepository: TaskRepository
) : BaseBuiltinTool() {

    override val definition: BuiltinToolDefinition = BuiltinToolDefinition(
        name = "cli::create_task",
        description = "Создаёт новую задачу и устанавливает её как текущую. После вызова все остальные cli::* инструменты будут работать с этой задачей.",
        parametersJsonSchema = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Название задачи (3-200 символов)"
                    },
                    "description": {
                        "type": "string",
                        "description": "Описание задачи (опционально, до 2000 символов)"
                    }
                },
                "required": ["name"]
            }
        """.trimIndent()
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolExecutionResult {
        val name = arguments["name"] as? String
        if (name.isNullOrBlank()) {
            return jsonError("Ошибка: Название задачи не может быть пустым")
        }
        if (name.length < MIN_TASK_NAME_LENGTH) {
            return jsonError("Ошибка: Название слишком короткое (мин. $MIN_TASK_NAME_LENGTH символов)")
        }
        if (name.length > MAX_TASK_NAME_LENGTH) {
            return jsonError("Ошибка: Название слишком длинное (макс. $MAX_TASK_NAME_LENGTH символов)")
        }

        val rawDescription = (arguments["description"] as? String)?.takeIf { it.isNotBlank() }
        if (rawDescription != null && rawDescription.length > MAX_TASK_DESCRIPTION_LENGTH) {
            return jsonError("Ошибка: Описание слишком длинное (макс. $MAX_TASK_DESCRIPTION_LENGTH символов)")
        }
        val description = rawDescription
        val task = todoTaskService.addTask(name, description)
        val reloadedTask = taskRepository.findById(task.id) ?: task

        return ToolExecutionResult(
            text = "✓ Задача #${task.id} создана: '${task.title}'",
            updatedContext = BuiltinToolContext(currentTask = reloadedTask)
        )
    }
}
