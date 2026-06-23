package io.averkhogliad.ai.challenge.week2.cli.handlers

import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week2.cli.CliRenderer
import io.averkhogliad.ai.challenge.week2.cli.CliState
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TransitionNotAllowedException
import io.averkhogliad.ai.challenge.week2.domain.service.CommandEngine
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.domain.service.ProfileRepository

/**
 * Handler для обработки FSM-команд (Finite State Machine).
 *
 * Отвечает за:
 * - Команды навигации по состояниям FSM (`:goto`, `:goto <state>`)
 * - Отображение статуса FSM и доступных переходов
 * - Визуализацию состояния FSM в debug-режиме
 *
 * @param commandEngine движок управления состояниями FSM
 * @param DebugCommandHandler исполнитель для debug-режима
 * @param memoryService сервис управления памятью (для статуса)
 * @param profileRepository репозиторий профилей (для статуса)
 * @param invariantService сервис инвариантов (для статуса)
 * @param renderer рендерер CLI вывода
 */
class FsmCommandHandler(
    private val commandEngine: CommandEngine,
    private val debugCommandHandler: DebugCommandHandler,
    private val memoryService: MemoryService,
    private val profileRepository: ProfileRepository,
    private val invariantService: InvariantService,
    private val renderer: CliRenderer
) {

    /**
     * Обрабатывает команду `:goto` — показывает карту состояний FSM.
     *
     * @param state текущее состояние CLI
     * @return обновленное состояние CLI
     */
    fun handleGoto(state: CliState): CliState {
        if (!commandEngine.hasActiveCommand()) {
            renderer.renderGotoNoActiveCommand()
        } else {
            val stateMap = commandEngine.buildStateMap()
            renderer.renderStateMap(stateMap)
        }
        return state
    }

    /**
     * Обрабатывает команду `:goto <state>` — выполняет переход к указанному состоянию.
     *
     * @param command команда GotoState
     * @param state текущее состояние CLI
     * @return обновленное состояние CLI
     */
    fun handleGotoState(command: Command.GotoState, state: CliState): CliState {
        if (!commandEngine.hasActiveCommand()) {
            renderer.renderGotoNoActiveCommand()
            return state
        }

        // Разбор имени состояния
        val targetStage = try {
            CommandStage.valueOf(command.targetStage)
        } catch (_: IllegalArgumentException) {
            renderer.renderGotoInvalidState(command.targetStage)
            return state
        }

        try {
            val activeState = commandEngine.getActiveState()
            val from = activeState?.currentStage
            commandEngine.performTransition(targetStage, ":goto command")
            renderer.renderGotoSuccess(
                from = from ?: CommandStage.PLANNING,
                to = targetStage
            )
        } catch (e: TransitionNotAllowedException) {
            renderer.renderGotoError(e.message ?: "Переход недопустим")
        } catch (e: Exception) {
            renderer.renderGotoError(e.message ?: "Ошибка перехода")
        }

        return state
    }

    /**
     * Обрабатывает команду `:status` — показывает полную информацию о состоянии системы.
     *
     * @param state текущее состояние CLI
     * @return обновленное состояние CLI
     */
    suspend fun handleShowStatus(state: CliState): CliState {
        try {
            val level = if (state.currentTodoTaskId != null) {
                SessionLevel.TASK_DETAIL
            } else {
                SessionLevel.TASK_LIST
            }
            val taskId = state.currentTodoTaskId?.let { TaskId(it) }
            val status = memoryService.getMemoryStatus(level, taskId)
            if (status != null) {
                renderer.renderMemoryStatus(status)
            } else {
                renderer.renderInfo("Memory service not available")
            }

            // Отображение информации о профиле
            val activeProfile = profileRepository.findActive()
            renderer.renderStatusProfile(activeProfile?.name)

            // US-DBG-5: Отображение статуса debug-режима
            val isDebugEnabled = debugCommandHandler.isEnabled()
            renderer.renderStatusDebug(isDebugEnabled)

            // US-STATUS-1: Отображение статуса FSM с доступными переходами
            val activeState = commandEngine.getActiveState()
            val availableTransitions = if (activeState != null) {
                commandEngine.getAvailableTransitions()
            } else {
                emptyList()
            }
            renderer.renderStatusFsm(activeState?.currentStage, availableTransitions)

            // Отображение количества инвариантов
            val invariantCount = invariantService.count()
            renderer.renderStatusInvariants(invariantCount)

        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }

        return state
    }

    /**
     * Визуализирует состояние FSM, если debug-режим включен.
     * Вызывается после обработки FSM-команд для отладки.
     * В debug-режиме также вызывает паузу для пошагового просмотра.
     */
    fun renderFsmStateIfDebug() {
        if (debugCommandHandler.isEnabled() && commandEngine.hasActiveCommand()) {
            val activeState = commandEngine.getActiveState()
            if (activeState != null) {
                renderer.renderFsmState(activeState)
                // US-DBG-1: Show available transitions after each debug step
                val availableTransitions = commandEngine.getAvailableTransitions()
                if (availableTransitions != null && availableTransitions.isNotEmpty()) {
                    renderer.renderAvailableTransitions(availableTransitions)
                }
                // US-DBG-4: Pause after step in debug mode
                renderer.waitForEnter()
            }
        }
    }
}
