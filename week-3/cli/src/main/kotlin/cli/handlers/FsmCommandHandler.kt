package io.averkhogliad.ai.challenge.week3.cli.cli.handlers


import io.averkhogliad.ai.challenge.week3.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week3.cli.cli.CliState
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage

import io.averkhogliad.ai.challenge.week3.cli.domain.model.TransitionNotAllowedException
import io.averkhogliad.ai.challenge.week3.cli.domain.service.CommandEngine


/**
 * Handler для обработки FSM-команд (Finite State Machine).
 *
 * Отвечает за:
 * - Команды навигации по состояниям FSM (`:goto`, `:goto <state>`)

 *
 * @param commandEngine движок управления состояниями FSM
 * @param renderer рендерер CLI вывода

 */
class FsmCommandHandler(
    private val commandEngine: CommandEngine,
    private val renderer: CliRenderer,
    private val readInput: () -> String? = { readlnOrNull() }
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
     * @param state текущее состояние CLI
     * @return обновленное состояние CLI
     */
    fun handleShowState(state: CliState): CliState {
        val activeState = commandEngine.getActiveState()
        if (activeState == null) {
            renderer.renderNoActiveCommand()
        } else {
            renderer.renderFsmStateInfo(activeState)
        }
        return state
    }

    fun handleAbort(state: CliState): CliState {
        if (!commandEngine.hasActiveCommand()) {
            renderer.renderNoActiveCommand()
            return state
        }

        renderer.renderAbortConfirmation()
        val confirmation = readInput()?.trim()?.lowercase()
        if (confirmation == "y" || confirmation == "yes") {
            commandEngine.abortCommand()
            renderer.renderAbortSuccess()
        } else {
            renderer.renderAbortCancelled()
        }
        return state
    }

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

}
