package io.averkhogliad.ai.challenge.week3.cli.cli.handlers

import io.averkhogliad.ai.challenge.week3.cli.application.DialogService
import io.averkhogliad.ai.challenge.week3.cli.application.handler.PlanCommandHandler
import io.averkhogliad.ai.challenge.week3.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week3.cli.cli.CliState
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId

class PlanFlowHandler(
    private val renderer: CliRenderer,
    private val dialogService: DialogService,
    private val planCommandHandler: PlanCommandHandler,
) {
    suspend fun handlePlan(state: CliState): CliState {
        val taskId = state.currentTodoTaskId?.toIntOrNull()
        val result = planCommandHandler.execute(taskId)
        renderer.renderInfo(result)
        return state
    }

    suspend fun handlePlanSteps(command: Command.PlanSteps, state: CliState): CliState {
        renderer.renderLoadingStart("Запрос плана шагов...")
        val result = dialogService.planSteps(
            command.title,
            command.description,
            state.sessionLevel(),
            state.currentTodoTaskId?.let { TaskId(it) }
        )
        renderer.renderLoadingStop()
        when (result) {
            is TaskResult.Success -> renderer.renderResult(result)
            is TaskResult.Error -> renderer.renderError(result.message)
            is TaskResult.Partial -> renderer.renderResult(result)
        }
        return state
    }

    private fun CliState.sessionLevel(): SessionLevel =
        if (currentTodoTaskId != null) SessionLevel.TASK_DETAIL else SessionLevel.TASK_LIST
}
