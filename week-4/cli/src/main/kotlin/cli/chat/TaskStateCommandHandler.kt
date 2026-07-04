package io.averkhogliad.ai.challenge.week4.cli.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.TaskStateManager
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import java.util.*

/**
 * Обработчик команд управления памятью задачи ([TaskState]).
 *
 * Применяет [TaskStateCommand] через [TaskStateManager] и возвращает обновлённое [CliState].
 */
class TaskStateCommandHandler(
    private val taskStateManager: TaskStateManager,
    private val renderer: TaskStateRenderer,
    private val notificationRenderer: ChatNotificationRenderer
) {

    /**
     * Диспетчеризует [TaskStateCommand] и возвращает обновлённое состояние.
     */
    suspend fun handle(command: TaskStateCommand, state: CliState): CliState = when (command) {
        is TaskStateCommand.Show -> handleShow(state)
        is TaskStateCommand.Reset -> handleReset(state)
        is TaskStateCommand.SetGoal -> handleSetGoal(command, state)
        is TaskStateCommand.AddTerm -> handleAddTerm(command, state)
        is TaskStateCommand.RemoveTerm -> handleRemoveTerm(command, state)
        is TaskStateCommand.AddConstraint -> handleAddConstraint(command, state)
        is TaskStateCommand.RemoveConstraint -> handleRemoveConstraint(command, state)
    }

    private fun requireSessionId(state: CliState): UUID? {
        val sessionId = state.activeChatSessionId ?: run {
            notificationRenderer.renderError("Нет активного чата")
            return null
        }
        return try {
            UUID.fromString(sessionId)
        } catch (e: IllegalArgumentException) {
            notificationRenderer.renderError("Неверный формат ID: $sessionId")
            null
        }
    }

    private suspend fun handleShow(state: CliState): CliState {
        val sessionId = requireSessionId(state) ?: return state
        val taskState = taskStateManager.getTaskState(sessionId)
        renderer.render(taskState)
        return state
    }

    private suspend fun handleReset(state: CliState): CliState {
        val sessionId = requireSessionId(state) ?: return state
        taskStateManager.resetTaskState(sessionId)
        notificationRenderer.renderTaskStateReset()
        return state
    }

    private suspend fun handleSetGoal(command: TaskStateCommand.SetGoal, state: CliState): CliState {
        val sessionId = requireSessionId(state) ?: return state
        taskStateManager.setGoal(sessionId, command.text)
        notificationRenderer.renderTaskStateUpdated("Цель обновлена")
        return state
    }

    private suspend fun handleAddTerm(command: TaskStateCommand.AddTerm, state: CliState): CliState {
        val sessionId = requireSessionId(state) ?: return state
        taskStateManager.addTerm(sessionId, command.name, command.definition)
        notificationRenderer.renderTaskStateUpdated("Термин '${command.name}' добавлен")
        return state
    }

    private suspend fun handleRemoveTerm(command: TaskStateCommand.RemoveTerm, state: CliState): CliState {
        val sessionId = requireSessionId(state) ?: return state
        taskStateManager.removeTerm(sessionId, command.name)
        notificationRenderer.renderTaskStateUpdated("Термин '${command.name}' удалён")
        return state
    }

    private suspend fun handleAddConstraint(command: TaskStateCommand.AddConstraint, state: CliState): CliState {
        val sessionId = requireSessionId(state) ?: return state
        taskStateManager.addConstraint(sessionId, command.text)
        notificationRenderer.renderTaskStateUpdated("Ограничение добавлено")
        return state
    }

    private suspend fun handleRemoveConstraint(
        command: TaskStateCommand.RemoveConstraint,
        state: CliState
    ): CliState {
        val sessionId = requireSessionId(state) ?: return state
        taskStateManager.removeConstraint(sessionId, command.index)
        notificationRenderer.renderTaskStateUpdated("Ограничение #${command.index} удалено")
        return state
    }
}
