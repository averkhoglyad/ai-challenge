package io.averkhogliad.ai.challenge.week4.cli.infrastructure.tool

import io.averkhogliad.ai.challenge.week4.cli.domain.model.BuiltinToolContext
import io.averkhogliad.ai.challenge.week4.cli.domain.model.BuiltinToolDefinition
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ToolExecutionResult
import java.time.LocalDate
import java.util.*

/**
 * Инструмент `cli::link_task_to_event` — привязывает текущую задачу к событию календаря.
 */
class LinkTaskToEventTool(
    private val taskRepository: TaskRepository
) : BaseBuiltinTool() {

    override val definition: BuiltinToolDefinition = BuiltinToolDefinition(
        name = "cli::link_task_to_event",
        description = "Привязывает текущую открытую задачу к созданному событию календаря. Сохраняет eventId и dueDate в задаче.",
        parametersJsonSchema = """
            {
                "type": "object",
                "properties": {
                    "eventId": {
                        "type": "string",
                        "description": "UUID события из events::create_event"
                    },
                    "dueDate": {
                        "type": "string",
                        "description": "Дата события в формате YYYY-MM-DD (ISO 8601)"
                    }
                },
                "required": ["eventId", "dueDate"]
            }
        """.trimIndent()
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: BuiltinToolContext
    ): ToolExecutionResult {
        val taskResult = requireTask(context)
        if (taskResult.isFailure) return jsonError(taskResult.exceptionOrNull()!!.message!!)

        val eventIdStr = arguments["eventId"] as? String
        if (eventIdStr.isNullOrBlank()) {
            return jsonError("Ошибка: Невалидный формат eventId (ожидается UUID)")
        }

        val eventId = try {
            UUID.fromString(eventIdStr)
        } catch (_: IllegalArgumentException) {
            return jsonError("Ошибка: Невалидный формат eventId (ожидается UUID)")
        }

        val dueDateStr = arguments["dueDate"] as? String
        if (dueDateStr.isNullOrBlank()) {
            return jsonError("Ошибка: Невалидный формат dueDate (ожидается YYYY-MM-DD)")
        }

        val dueDate = try {
            LocalDate.parse(dueDateStr)
        } catch (_: Exception) {
            return jsonError("Ошибка: Невалидный формат dueDate (ожидается YYYY-MM-DD)")
        }

        val task = taskResult.getOrThrow()
        val updateResult = taskRepository.updateEvent(task.id, eventId, dueDate)

        if (updateResult.isFailure) {
            return jsonError("Ошибка при привязке события: ${updateResult.exceptionOrNull()?.message}")
        }

        val updatedTask = taskRepository.findById(task.id)
        return ToolExecutionResult(
            text = "✓ Задача #${task.id} привязана к событию $eventId на $dueDate",
            updatedContext = if (updatedTask != null) BuiltinToolContext(currentTask = updatedTask) else null
        )
    }
}
