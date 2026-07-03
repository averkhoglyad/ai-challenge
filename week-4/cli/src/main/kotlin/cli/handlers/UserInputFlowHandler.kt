package io.averkhogliad.ai.challenge.week4.cli.cli.handlers

import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.application.handler.PlanCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.CommandEngine

class UserInputFlowHandler(
    private val renderer: CliRenderer,
    private val dialogService: DialogService,
    private val planCommandHandler: PlanCommandHandler,
    private val commandEngine: CommandEngine,
) {
    suspend fun handle(command: Command.UserInput, state: CliState): CliState {
        if (commandEngine.hasActiveCommand()) {
            val activeState = commandEngine.getActiveState()
            if (activeState?.commandName == "plan") {
                val result = planCommandHandler.handleUserInput(command.text)
                renderer.renderInfo(result)
                return state
            }
        }

        if (state.currentTodoTaskId != null || state.taskListMode) {
            renderer.renderLoadingStart("Общение с ассистентом...")
            val result =
                dialogService.chat(command.text, state.sessionLevel(), state.currentTodoTaskId?.let { TaskId(it) })
            renderer.renderLoadingStop()
            renderTaskResult(result)
            return state
        }

        if (state.currentTaskId != null) {
            renderer.renderRequestInfo(command.text, state.executionConfig)
            renderer.renderLoadingStart("Отправка запроса...")
            val result = dialogService.chat(command.text, state.sessionLevel(), null)
            renderer.renderLoadingStop()
            renderTaskResult(result)
            return state
        }

        renderer.renderLoadingStart("Общение с ассистентом...")
        val result = dialogService.chat(command.text, state.sessionLevel(), null)
        renderer.renderLoadingStop()
        renderTaskResult(result)
        return state

    }

    private fun CliState.sessionLevel(): SessionLevel =
        if (currentTodoTaskId != null) SessionLevel.TASK_DETAIL else SessionLevel.TASK_LIST

    private fun renderTaskResult(result: TaskResult?, onNull: () -> Unit = {}) {
        when (result) {
            is TaskResult.Success -> renderer.renderResult(result)
            is TaskResult.Error -> renderer.renderError(result.message)
            is TaskResult.Partial -> renderer.renderResult(result)
            null -> onNull()
        }
    }
}
