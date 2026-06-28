package io.averkhogliad.ai.challenge.week3.cli.application

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.CommandEngine
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TransitionValidator

/**
 * Реализация FSM-движка для управления выполнением команд.
 * 
 * Архитектурное расположение: application layer (orchestration)
 * 
 * Отвечает за:
 * - Хранение активного состояния команды
 * - Создание состояния при старте команды
 * - Переход между этапами и шагами через делегирование к CommandState
 * - Уничтожение состояния после завершения или отмены
 * 
 * Особенности:
 * - Состояние не персистентно (теряется при завершении сессии)
 * - Одновременно может выполняться только одна команда
 * - Все операции с состоянием проверяют наличие активной команды
 */
class DefaultCommandEngine(
    private val transitionValidator: TransitionValidator = TransitionValidator(
        TransitionValidator.planTransitions()
    )
) : CommandEngine {

    /**
     * Активное состояние команды.
     * Null если нет выполняемой команды.
     */
    private var activeState: CommandState? = null

    override fun hasActiveCommand(): Boolean = activeState != null

    override fun getActiveState(): CommandState? = activeState

    override fun startCommand(commandName: String, initialAction: String) {
        require(commandName.isNotBlank()) { "Command name cannot be blank" }

        val currentState = activeState
        if (currentState != null) {
            throw IllegalStateException(
                "Cannot start command '$commandName': another command '${currentState.commandName}' is already active"
            )
        }

        activeState = CommandState(
            commandName = commandName,
            currentStage = CommandStage.PLANNING,
            currentStep = 1,
            expectedAction = initialAction,
            context = emptyMap()
        )
    }

    override fun advanceToStage(expectedAction: String) {
        val currentState = requireActiveState()

        val nextStage = when (currentState.currentStage) {
            CommandStage.PLANNING -> CommandStage.EXECUTION
            CommandStage.EXECUTION -> CommandStage.VALIDATION
            CommandStage.VALIDATION -> CommandStage.DONE
            CommandStage.DONE -> throw IllegalStateException(
                "Cannot advance from DONE stage. Command '${currentState.commandName}' is complete."
            )

            CommandStage.TERMINATED -> throw IllegalStateException(
                "Cannot advance from TERMINATED stage. Command '${currentState.commandName}' is terminated."
            )
        }

        activeState = currentState.advanceToStage(nextStage, expectedAction)
    }

    override fun advanceToStage(stage: CommandStage, expectedAction: String) {
        val currentState = requireActiveState()
        activeState = currentState.advanceToStage(stage, expectedAction)
    }

    override fun advanceStep(expectedAction: String) {
        val currentState = requireActiveState()
        activeState = currentState.advanceStep(expectedAction)
    }

    override fun putContext(key: String, value: String) {
        val currentState = requireActiveState()
        activeState = currentState.putContext(key, value)
    }

    override fun getContext(key: String): String? {
        val currentState = requireActiveState()
        return currentState.getContext(key)
    }

    override fun completeCommand() {
        val currentState = requireActiveState()

        // Переход в DONE если ещё не там
        if (currentState.currentStage != CommandStage.DONE) {
            activeState = currentState.advanceToStage(CommandStage.DONE, "Command completed")
        }

        // Уничтожение состояния
        activeState = null
    }

    override fun abortCommand() {
        val currentState = requireActiveState()

        // Запись перехода в TERMINATED
        activeState = currentState
            .recordTransition(CommandStage.TERMINATED, "Aborted by user")
            .advanceToStage(CommandStage.TERMINATED, "Command aborted")

        // Принудительное уничтожение состояния
        activeState = null
    }

    override fun performTransition(to: CommandStage, description: String) {
        val currentState = requireActiveState()
        val context = currentState.context

        val validationResult = transitionValidator.canTransition(
            currentState.currentStage, to, context
        )

        if (!validationResult.allowed) {
            throw TransitionNotAllowedException(
                currentState.currentStage, to, validationResult
            )
        }

        activeState = currentState
            .recordTransition(to, description)
            .advanceToStage(to, description)
    }

    override fun isTransitionAllowed(to: CommandStage): TransitionValidationResult {
        val currentState = requireActiveState()
        return transitionValidator.canTransition(
            currentState.currentStage, to, currentState.context
        )
    }

    override fun getAvailableTransitions(): List<Transition> {
        val currentState = requireActiveState()
        return transitionValidator.getAvailableTransitions(
            currentState.currentStage, currentState.context
        )
    }

    override fun buildStateMap(): StateMap {
        val currentState = requireActiveState()
        return transitionValidator.buildStateMap(
            currentState.currentStage, currentState.context
        )
    }

    override fun pause() {
        val currentState = requireActiveState()
        activeState = currentState.pause()
    }

    /**
     * US-RESUME-1: Проверяет условия перехода при возобновлении после паузы.
     *
     * 1. Снимает флаг паузы
     * 2. Проверяет, что команда не завершена (TERMINATED)
     * 3. Проверяет, что текущее состояние всё ещё допустимо
     *
     * @throws IllegalStateException если команда была завершена во время паузы
     */
    override fun resume() {
        val currentState = requireActiveState()

        // Проверка: не была ли команда завершена во время паузы
        if (currentState.isTerminated()) {
            throw IllegalStateException(
                "Cannot resume: command '${currentState.commandName}' was terminated during pause."
            )
        }

        // Проверка: можно ли продолжить из текущего состояния?
        // Убеждаемся, что доступен хотя бы один переход
        val availableTransitions = transitionValidator.getAvailableTransitions(
            currentState.currentStage,
            currentState.context
        )

        // Если нет доступных переходов и состояние не DONE и не TERMINATED — возможно, условия изменились
        if (availableTransitions.isEmpty() && !currentState.isDone() && !currentState.isTerminated()) {
            throw IllegalStateException(
                "Cannot resume from '${currentState.currentStage}': " +
                        "conditions have changed during pause and no transitions are available. " +
                        "Use :goto to view available states or :abort to cancel the command."
            )
        }

        activeState = currentState.resume()
    }

    /**
     * Вспомогательный метод для получения активного состояния с проверкой.
     *
     * @throws IllegalStateException если нет активной команды
     */
    private fun requireActiveState(): CommandState {
        return activeState ?: throw IllegalStateException(
            "No active command. Start a command first using startCommand()."
        )
    }
}
