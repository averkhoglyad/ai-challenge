package io.averkhogliad.ai.challenge.week2.application

import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week2.domain.model.CommandState
import io.averkhogliad.ai.challenge.week2.domain.service.CommandEngine

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
class DefaultCommandEngine : CommandEngine {

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

        // Принудительное уничтожение состояния без перехода в DONE
        activeState = null
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
