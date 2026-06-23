package io.averkhogliad.ai.challenge.week2.cli.handlers

import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.cli.CliRenderer
import io.averkhogliad.ai.challenge.week2.cli.CliState
import io.averkhogliad.ai.challenge.week2.cli.commands.Command

/**
 * Handler для обработки команд управления инвариантами.
 *
 * Отвечает за:
 * - Добавление инвариантов (`:invariant add <rule>`)
 * - Список инвариантов (`:invariant list`)
 * - Удаление инвариантов (`:invariant remove <id>`)
 *
 * @param invariantService сервис управления инвариантами
 * @param renderer рендерер CLI вывода
 * @param readInput функция для чтения ввода пользователя (для подтверждения удаления)
 */
class InvariantCommandHandler(
    private val invariantService: InvariantService?,
    private val renderer: CliRenderer,
    private val readInput: () -> String? = { readlnOrNull() }
) {

    /**
     * Обрабатывает команду `:invariant add <rule>` — добавляет новый инвариант.
     *
     * @param command команда InvariantAdd
     * @param state текущее состояние CLI
     * @return обновленное состояние CLI
     */
    suspend fun handleInvariantAdd(command: Command.InvariantAdd, state: CliState): CliState {
        try {
            val inv = invariantService?.add(command.rule)
                ?: throw IllegalStateException("InvariantService not available")
            renderer.renderInvariantAdded(inv)
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    /**
     * Обрабатывает команду `:invariant list` — показывает список инвариантов.
     *
     * @param state текущее состояние CLI
     * @return обновленное состояние CLI
     */
    suspend fun handleInvariantList(state: CliState): CliState {
        try {
            val invariants = invariantService?.list()
                ?: throw IllegalStateException("InvariantService not available")
            renderer.renderInvariantList(invariants)
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    /**
     * Обрабатывает команду `:invariant remove <id>` — удаляет инвариант с подтверждением.
     *
     * @param command команда InvariantRemove
     * @param state текущее состояние CLI
     * @return обновленное состояние CLI
     */
    suspend fun handleInvariantRemove(command: Command.InvariantRemove, state: CliState): CliState {
        try {
            renderer.renderInvariantRemoveConfirmation(command.id)
            val confirmation = readInput()?.trim()?.lowercase()
            if (confirmation == "y" || confirmation == "yes") {
                val removed = invariantService?.remove(command.id) == true
                if (removed) {
                    renderer.renderInvariantRemoved(command.id)
                } else {
                    renderer.renderInvariantNotFound(command.id)
                }
            } else {
                renderer.renderInfo("Удаление отменено")
            }
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }
}
