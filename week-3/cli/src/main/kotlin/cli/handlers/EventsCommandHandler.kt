package io.averkhogliad.ai.challenge.week3.cli.cli.handlers

import io.averkhogliad.ai.challenge.week3.cli.application.usecase.CreateEventForTaskUseCase
import io.averkhogliad.ai.challenge.week3.cli.application.usecase.ListNotesUseCase
import io.averkhogliad.ai.challenge.week3.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week3.cli.cli.CliState
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.model.*

class EventsCommandHandler(
    private val createEventUseCase: CreateEventForTaskUseCase,
    private val listNotesUseCase: ListNotesUseCase,
    private val renderer: CliRenderer
) {
    suspend fun handleCreateEvent(command: Command.CreateEvent, state: CliState): CliState {
        val taskIdStr = state.currentTodoTaskId
        if (taskIdStr == null) {
            renderer.renderError("✗ Нет открытой задачи\n  Используйте :open <id> перед планированием")
            return state
        }

        val taskId = TaskId(taskIdStr)
        val result = createEventUseCase.execute(taskId, command.date)

        result.fold(
            onSuccess = { task ->
                renderer.renderInfo(
                    "✓ Событие создано\n" +
                            "  ID: ${task.eventId}\n" +
                            "  Задача: ${task.title}\n" +
                            "  Дата: ${task.dueDate}"
                )
            },
            onFailure = { error ->
                val message = when (error) {
                    is NoOpenTaskException -> "✗ ${error.message}"
                    is FSMActiveException -> "✗ Активная FSM-команда: ${error.activeState}\n  Завершите или отмените через :abort"
                    is InvalidDateException -> "✗ ${error.message}"
                    is EventsException.EventNotFound -> "✗ Событие не найдено: ${error.id}"
                    is EventsException.ValidationFailed -> "✗ Ошибка валидации: ${error.message}"
                    is EventsException.ServerError -> "✗ Ошибка events-server: ${error.code}\n  Детали: ${error.message}"
                    is EventsException.ConnectionFailed -> "✗ Ошибка соединения: ${error.cause.message}"
                    else -> "✗ Неожиданная ошибка: ${error.message}"
                }
                renderer.renderError(message)
            }
        )

        return state
    }

    suspend fun handleListNotes(command: Command.ListNotes, state: CliState): CliState {
        val limit = command.limit ?: 20
        val result = listNotesUseCase.execute(limit)

        result.fold(
            onSuccess = { notifications ->
                if (notifications.isEmpty()) {
                    renderer.renderInfo("📬 Уведомления (0)\n  (нет уведомлений)")
                } else {
                    val sb = StringBuilder()
                    sb.appendLine("📬 Уведомления (${notifications.size}):")
                    sb.appendLine()
                    for (n in notifications) {
                        sb.appendLine("[${n.createdAt}] ${n.title}")
                        sb.appendLine("  ${n.message}")
                        sb.appendLine()
                    }
                    renderer.renderResult(TaskResult.Success(sb.toString()))
                }
            },
            onFailure = { error ->
                val message = when (error) {
                    is NotificationsException.ServerError -> "✗ Ошибка notification-server: ${error.code}\n  Детали: ${error.message}"
                    is NotificationsException.ConnectionFailed -> "✗ Ошибка соединения: ${error.cause.message}"
                    else -> "✗ Неожиданная ошибка: ${error.message}"
                }
                renderer.renderError(message)
            }
        )

        return state
    }
}
