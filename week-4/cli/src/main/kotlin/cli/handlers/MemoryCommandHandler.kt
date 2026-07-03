package io.averkhogliad.ai.challenge.week4.cli.cli.handlers

import io.averkhogliad.ai.challenge.week4.cli.application.InvariantService
import io.averkhogliad.ai.challenge.week4.cli.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.CommandEngine
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ProfileRepository

class MemoryCommandHandler(
    private val memoryService: MemoryService,
    private val profileRepository: ProfileRepository,
    private val debugCommandHandler: DebugCommandHandler,
    private val commandEngine: CommandEngine,
    private val invariantService: InvariantService,
    private val renderer: CliRenderer,
) {

    suspend fun handleClearMemory(state: CliState): CliState =
        handleMemoryError(state) {
            val (level, taskId) = currentMemoryTarget(state)
            memoryService.clearSession(level, taskId)
            renderer.renderMemoryCleared()
            state
        }

    suspend fun handleShowStatus(state: CliState): CliState =
        handleMemoryError(state) {
            val (level, taskId) = currentMemoryTarget(state)
            val status = memoryService.getMemoryStatus(level, taskId)
            renderer.renderMemoryStatus(status)

            val activeProfile = profileRepository.findActive()
            renderer.renderStatusProfile(activeProfile?.name)

            val isDebugEnabled = debugCommandHandler.isEnabled()
            renderer.renderStatusDebug(isDebugEnabled)

            val activeState = commandEngine.getActiveState()
            val availableTransitions = if (activeState != null) {
                commandEngine.getAvailableTransitions()
            } else {
                emptyList()
            }
            renderer.renderStatusFsm(activeState?.currentStage, availableTransitions)

            val invariantCount = invariantService.count()
            renderer.renderStatusInvariants(invariantCount)
            state
        }

    private fun currentMemoryTarget(state: CliState): Pair<SessionLevel, TaskId?> {
        val taskId = state.currentTodoTaskId?.let { TaskId(it) }
        val level = if (taskId != null) {
            SessionLevel.TASK_DETAIL
        } else {
            SessionLevel.TASK_LIST
        }
        return level to taskId
    }

    private inline fun handleMemoryError(state: CliState, action: () -> CliState): CliState =
        try {
            action()
        } catch (e: Exception) {
            renderer.renderError(e.message ?: UNKNOWN_ERROR_MESSAGE)
            state
        }

    companion object {
        private const val UNKNOWN_ERROR_MESSAGE = "Unknown error"
    }
}
