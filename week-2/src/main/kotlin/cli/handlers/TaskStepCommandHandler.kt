package io.averkhogliad.ai.challenge.week2.cli.handlers

import io.averkhogliad.ai.challenge.week2.application.service.TaskStepService
import io.averkhogliad.ai.challenge.week2.cli.CliRenderer
import io.averkhogliad.ai.challenge.week2.cli.CliState
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId

class TaskStepCommandHandler(
    private val taskStepService: TaskStepService,
    private val renderer: CliRenderer,
) {

    suspend fun handleAddStep(command: Command.AddStep, state: CliState): CliState =
        handleStepError(state) {
            val taskId = requireTaskId(state)
            taskStepService.addStep(taskId, command.text)
            renderer.renderInfo(STEP_ADDED_MESSAGE)
            state
        }

    fun handleListSteps(state: CliState): CliState =
        handleStepError(state) {
            val taskId = requireTaskId(state)
            val steps = taskStepService.listSteps(taskId)
            renderer.renderStepList(steps)
            state
        }


    suspend fun handleCompleteStep(command: Command.CompleteStep, state: CliState): CliState =
        handleStepError(state) {
            requireTaskId(state)
            taskStepService.completeStep(command.stepId)
            renderer.renderInfo(STEP_COMPLETED_MESSAGE)
            state
        }

    private fun requireTaskId(state: CliState): TaskId =
        TaskId(
            requireNotNull(state.currentTodoTaskId) {
                NO_TASK_OPEN_MESSAGE
            }
        )

    private inline fun handleStepError(state: CliState, action: () -> CliState): CliState =
        try {
            action()
        } catch (e: Exception) {
            renderer.renderError(e.message ?: UNKNOWN_ERROR_MESSAGE)
            state
        }

    companion object {
        private const val STEP_ADDED_MESSAGE = "Step added"
        private const val STEP_COMPLETED_MESSAGE = "Step completed"
        private const val NO_TASK_OPEN_MESSAGE = "No task is currently open"
        private const val UNKNOWN_ERROR_MESSAGE = "Unknown error"
    }
}
