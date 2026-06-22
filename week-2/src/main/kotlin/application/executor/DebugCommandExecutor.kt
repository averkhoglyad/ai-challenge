package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.cli.commands.Command.DebugAction
import io.averkhogliad.ai.challenge.week2.domain.model.DebugMode

/**
 * Executor для команды `:debug` — управление debug-режимом FSM.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация бизнес-операции
 * - **Single Responsibility** — отвечает только за управление debug-режимом
 * - **Stateless** — не использует FSM-состояния (простая команда)
 *
 * ## Поддерживаемые действия
 * - `TOGGLE` — переключить текущее состояние
 * - `ON` — включить debug-режим
 * - `OFF` — выключить debug-режим
 *
 * ## Использование
 * ```kotlin
 * val debugMode = DebugMode()
 * val executor = DebugCommandExecutor(debugMode)
 * executor.execute(DebugAction.TOGGLE) // переключит режим
 * ```
 */
class DebugCommandExecutor(
    private val debugMode: DebugMode
) {

    val commandName: String = "debug"

    /**
     * Выполняет команду управления debug-режимом.
     *
     * @param action действие для выполнения (TOGGLE, ON, OFF)
     * @return строка с результатом выполнения
     */
    fun execute(action: DebugAction): String {
        when (action) {
            DebugAction.TOGGLE -> {
                debugMode.toggle()
                return if (debugMode.isEnabled) "Debug mode enabled" else "Debug mode disabled"
            }

            DebugAction.ON -> {
                if (debugMode.isEnabled) {
                    return "Debug mode already enabled"
                }
                debugMode.enable()
                return "Debug mode enabled"
            }

            DebugAction.OFF -> {
                if (!debugMode.isEnabled) {
                    return "Debug mode already disabled"
                }
                debugMode.disable()
                return "Debug mode disabled"
            }
        }
    }

    /**
     * Возвращает текущее состояние debug-режима.
     *
     * @return true если debug-режим включен, false иначе
     */
    fun isEnabled(): Boolean = debugMode.isEnabled
}
