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
                val state = if (debugMode.isEnabled) "включен" else "выключен"
                return "Debug-режим $state."
            }

            DebugAction.ON -> {
                debugMode.enable()
                return "Debug-режим включен."
            }

            DebugAction.OFF -> {
                debugMode.disable()
                return "Debug-режим выключен."
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
